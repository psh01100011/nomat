package com.dogdog.nomat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaSourceType;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.map.service.AudioExtractionCommandRunner;
import com.dogdog.nomat.domain.map.service.AudioProcessingJobService;
import com.dogdog.nomat.domain.map.service.AudioProcessingService;
import com.dogdog.nomat.domain.map.service.AudioProcessingTask;
import com.dogdog.nomat.domain.map.service.YoutubeAudioExtractionCommand;
import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Tag("audio-load")
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, AudioProcessingLoadIntegrationTest.FakeExternalServices.class})
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.audio-processing.enabled=false",
        "app.asset.cleanup.enabled=false",
        "app.room.game-timer.initial-delay-ms=3600000",
        "app.asset.s3.bucket=load-test",
        "app.asset.s3.public-base-url=https://cdn.example.com"
})
class AudioProcessingLoadIntegrationTest {

    private static final int MAP_COUNT = 10;
    private static final int JOBS_PER_MAP = 50;
    private static final int TOTAL_JOB_COUNT = MAP_COUNT * JOBS_PER_MAP;
    private static final int WORKER_COUNT = 4;
    private static final long BENCHMARK_EXTRACTION_DELAY_MS = 20;
    private static final String WORKER_RESULT_PREFIX = "AUDIO_LOAD_WORKER_RESULT processed=";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379)
        );
    }

    @Autowired
    private AudioProcessingJobService jobService;

    @Autowired
    private AudioProcessingService processingService;

    @Autowired
    private AudioProcessingJobRepository jobRepository;

    @Autowired
    private QuizMapRepository mapRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FakeAudioExtractionCommandRunner extractionRunner;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private org.testcontainers.mysql.MySQLContainer mysqlContainer;

    @Test
    void processesFiveHundredJobsWithoutDuplicatesOrStuckMaps() throws Exception {
        LoadFixture fixture = createQueuedJobs();
        long assetCountBefore = assetRepository.count();
        AtomicInteger processedCount = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(WORKER_COUNT);

        try {
            var results = IntStream.range(0, WORKER_COUNT)
                    .mapToObj(ignored -> workers.submit(() -> processUntilQueueIsEmpty(processedCount)))
                    .toList();
            for (Future<?> result : results) {
                result.get();
            }
        } finally {
            workers.shutdownNow();
        }

        assertThat(processedCount.get()).isEqualTo(TOTAL_JOB_COUNT);
        assertCompletedFixture(fixture);
        assertThat(assetRepository.count()).isEqualTo(assetCountBefore + TOTAL_JOB_COUNT);
        assertThat(extractionRunner.getInvocationCount()).isEqualTo(TOTAL_JOB_COUNT);
        assertThat(extractionRunner.getUniqueOutputCount()).isEqualTo(TOTAL_JOB_COUNT);
        verify(s3Client, times(TOTAL_JOB_COUNT))
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void twoBackendWorkerProcessesDistributeFiveHundredJobs() throws Exception {
        LoadFixture fixture = createQueuedJobs();
        long assetCountBefore = assetRepository.count();
        Path processDirectory = Files.createTempDirectory("nomat-audio-load-processes-");
        Path startFile = processDirectory.resolve("start");
        WorkerProcess first = startWorkerProcess(processDirectory, startFile, 1, WorkerMode.NORMAL);
        WorkerProcess second = startWorkerProcess(processDirectory, startFile, 2, WorkerMode.NORMAL);

        try {
            awaitWorkerReady(first);
            awaitWorkerReady(second);
            Files.writeString(startFile, "start");

            assertWorkerCompleted(first);
            assertWorkerCompleted(second);

            int firstProcessed = readProcessedCount(first.logFile());
            int secondProcessed = readProcessedCount(second.logFile());
            assertThat(firstProcessed).isPositive();
            assertThat(secondProcessed).isPositive();
            assertThat(firstProcessed + secondProcessed).isEqualTo(TOTAL_JOB_COUNT);
        } finally {
            first.process().destroyForcibly();
            second.process().destroyForcibly();
        }

        assertCompletedFixture(fixture);
        assertThat(assetRepository.count()).isEqualTo(assetCountBefore + TOTAL_JOB_COUNT);
    }

    @Test
    void anotherProcessRecoversAJobAfterTheProcessingWorkerIsKilled() throws Exception {
        LoadFixture fixture = createQueuedJobs();
        long assetCountBefore = assetRepository.count();
        Path processDirectory = Files.createTempDirectory("nomat-audio-load-failover-");
        Path firstStartFile = processDirectory.resolve("first.start");
        WorkerProcess stoppedWorker = startWorkerProcess(
                processDirectory,
                firstStartFile,
                1,
                WorkerMode.BLOCK_DURING_EXTRACTION
        );

        Long abandonedJobId;
        try {
            awaitWorkerReady(stoppedWorker);
            Files.writeString(firstStartFile, "start");
            awaitWorkerProcessing(stoppedWorker);
            abandonedJobId = findSingleProcessingJob(fixture);
            stoppedWorker.process().destroyForcibly();
            assertThat(stoppedWorker.process().waitFor(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            stoppedWorker.process().destroyForcibly();
        }

        makeJobStale(abandonedJobId);

        Path recoveryStartFile = processDirectory.resolve("recovery.start");
        WorkerProcess recoveryWorker = startWorkerProcess(
                processDirectory,
                recoveryStartFile,
                2,
                WorkerMode.RECOVER_STALE
        );
        try {
            awaitWorkerReady(recoveryWorker);
            Files.writeString(recoveryStartFile, "start");
            assertWorkerCompleted(recoveryWorker);
            assertThat(readProcessedCount(recoveryWorker.logFile())).isEqualTo(TOTAL_JOB_COUNT);
        } finally {
            recoveryWorker.process().destroyForcibly();
        }

        assertCompletedFixture(fixture);
        AudioProcessingJob recoveredJob = jobRepository.findById(abandonedJobId).orElseThrow();
        assertThat(recoveredJob.getAttemptCount()).isEqualTo(2);
        assertThat(assetRepository.count()).isEqualTo(assetCountBefore + TOTAL_JOB_COUNT);
    }

    @Test
    void comparesThroughputAndClaimLatencyByServerAndWorkerCount() throws Exception {
        List<BenchmarkScenario> scenarios = List.of(
                new BenchmarkScenario(1, 1),
                new BenchmarkScenario(1, 2),
                new BenchmarkScenario(2, 1),
                new BenchmarkScenario(1, 4),
                new BenchmarkScenario(2, 2),
                new BenchmarkScenario(4, 1)
        );
        List<BenchmarkResult> results = new ArrayList<>(scenarios.size());

        for (BenchmarkScenario scenario : scenarios) {
            results.add(runBenchmarkScenario(scenario));
        }

        System.out.println("AUDIO_LOAD_BENCHMARK serverCount,workerConcurrency,totalWorkers,elapsedMs,jobsPerSecond,queueWaitP50Ms,queueWaitP95Ms,claimP50Micros,claimP95Micros");
        results.forEach(result -> System.out.println("AUDIO_LOAD_BENCHMARK " + result.toCsv()));
        assertThat(results).hasSize(scenarios.size());
    }

    private BenchmarkResult runBenchmarkScenario(BenchmarkScenario scenario) throws Exception {
        Path processDirectory = Files.createTempDirectory(
                "nomat-audio-benchmark-%dx%d-".formatted(scenario.serverCount(), scenario.workerConcurrency())
        );
        Path startFile = processDirectory.resolve("start");
        List<WorkerProcess> workers = new ArrayList<>(scenario.serverCount());

        try {
            for (int sequence = 1; sequence <= scenario.serverCount(); sequence++) {
                workers.add(startWorkerProcess(
                        processDirectory,
                        startFile,
                        sequence,
                        WorkerMode.BENCHMARK,
                        scenario.workerConcurrency()
                ));
            }
            for (WorkerProcess worker : workers) {
                awaitWorkerReady(worker);
            }

            long assetCountBefore = assetRepository.count();
            LoadFixture fixture = createQueuedJobs();
            long startedAt = System.nanoTime();
            Files.writeString(startFile, "start");
            for (WorkerProcess worker : workers) {
                assertWorkerCompleted(worker);
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            int processedCount = 0;
            List<Long> queueWaitMillis = new ArrayList<>(TOTAL_JOB_COUNT);
            List<Long> claimMicros = new ArrayList<>(TOTAL_JOB_COUNT);
            for (WorkerProcess worker : workers) {
                processedCount += readProcessedCount(worker.logFile());
                readWorkerMetrics(worker.metricsFile(), queueWaitMillis, claimMicros);
            }

            assertThat(processedCount).isEqualTo(TOTAL_JOB_COUNT);
            assertThat(queueWaitMillis).hasSize(TOTAL_JOB_COUNT);
            assertThat(claimMicros).hasSize(TOTAL_JOB_COUNT);
            assertCompletedFixture(fixture);
            assertThat(assetRepository.count()).isEqualTo(assetCountBefore + TOTAL_JOB_COUNT);

            return new BenchmarkResult(
                    scenario,
                    elapsedMillis,
                    TOTAL_JOB_COUNT * 1_000.0 / elapsedMillis,
                    percentile(queueWaitMillis, 50),
                    percentile(queueWaitMillis, 95),
                    percentile(claimMicros, 50),
                    percentile(claimMicros, 95)
            );
        } finally {
            workers.forEach(worker -> worker.process().destroyForcibly());
        }
    }

    private void processUntilQueueIsEmpty(AtomicInteger processedCount) {
        while (true) {
            List<AudioProcessingTask> tasks = jobService.claimProcessableJobs(1);
            if (tasks.isEmpty()) {
                return;
            }
            processingService.processClaimedJob(tasks.getFirst());
            processedCount.incrementAndGet();
        }
    }

    private LoadFixture createQueuedJobs() {
        return transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User creator = User.create("load-" + suffix, "encoded-password", "load-" + suffix);
            Category category = Category.create("load-category-" + suffix);
            entityManager.persist(creator);
            entityManager.persist(category);

            List<QuizMap> maps = new java.util.ArrayList<>(MAP_COUNT);
            List<AudioProcessingJob> jobs = new java.util.ArrayList<>(TOTAL_JOB_COUNT);
            IntStream.rangeClosed(1, MAP_COUNT)
                    .forEach(mapSequence -> maps.add(createMap(
                            creator,
                            category,
                            mapSequence,
                            jobs
                    )));
            entityManager.flush();
            return new LoadFixture(
                    maps.stream().map(QuizMap::getId).toList(),
                    jobs.stream().map(AudioProcessingJob::getId).toList()
            );
        });
    }

    private QuizMap createMap(
            User creator,
            Category category,
            int mapSequence,
            List<AudioProcessingJob> jobs
    ) {
        QuizMap map = QuizMap.create(
                creator,
                category,
                null,
                QuestionType.AUDIO,
                "부하 테스트 맵 " + mapSequence,
                "오디오 작업 부하 테스트",
                MapVisibility.PRIVATE,
                JOBS_PER_MAP,
                MapStatus.PROCESSING
        );
        entityManager.persist(map);
        IntStream.rangeClosed(1, JOBS_PER_MAP)
                .forEach(questionSequence -> jobs.add(createJob(map, mapSequence, questionSequence)));
        return map;
    }

    private AudioProcessingJob createJob(QuizMap map, int mapSequence, int questionSequence) {
        Question question = Question.create(map, questionSequence, "문제 " + questionSequence);
        entityManager.persist(question);
        QuestionMedia media = QuestionMedia.create(
                question,
                null,
                QuestionMediaSourceType.YOUTUBE,
                "https://youtube.com/watch?v=load-" + mapSequence + "-" + questionSequence,
                0,
                30_000,
                30_000
        );
        entityManager.persist(media);
        AudioProcessingJob job = AudioProcessingJob.create(media);
        entityManager.persist(job);
        return job;
    }

    private void assertCompletedFixture(LoadFixture fixture) {
        assertThat(jobRepository.findAllById(fixture.jobIds()))
                .hasSize(TOTAL_JOB_COUNT)
                .allSatisfy(job -> assertThat(job.getStatus())
                        .isEqualTo(AudioProcessingJobStatus.SUCCEEDED));
        assertThat(mapRepository.findAllById(fixture.mapIds()))
                .hasSize(MAP_COUNT)
                .allSatisfy(map -> assertThat(map.getStatus()).isEqualTo(MapStatus.PUBLISHED));
    }

    private WorkerProcess startWorkerProcess(
            Path directory,
            Path startFile,
            int sequence,
            WorkerMode mode
    ) throws Exception {
        return startWorkerProcess(directory, startFile, sequence, mode, 1);
    }

    private WorkerProcess startWorkerProcess(
            Path directory,
            Path startFile,
            int sequence,
            WorkerMode mode,
            int workerConcurrency
    ) throws Exception {
        Path readyFile = directory.resolve("worker-" + sequence + ".ready");
        Path logFile = directory.resolve("worker-" + sequence + ".log");
        Path metricsFile = directory.resolve("worker-" + sequence + ".csv");
        Path processingFile = directory.resolve("worker-" + sequence + ".processing");
        Path releaseFile = directory.resolve("worker-" + sequence + ".release");
        Path tempDirectory = directory.resolve("worker-" + sequence + "-tmp");
        Files.createDirectories(tempDirectory);
        String classpath = System.getProperty("audio.load-test.classpath");
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalStateException("Audio load test runtime classpath was not configured.");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.io.tmpdir=" + tempDirectory,
                "-cp",
                classpath,
                AudioProcessingLoadWorkerProcess.class.getName(),
                "--spring.datasource.url=" + mysqlContainer.getJdbcUrl(),
                "--spring.datasource.username=" + mysqlContainer.getUsername(),
                "--spring.datasource.password=" + mysqlContainer.getPassword(),
                "--spring.jpa.hibernate.ddl-auto=none",
                "--spring.flyway.enabled=false",
                "--spring.data.redis.url=redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379),
                "--app.audio-processing.enabled=false",
                "--app.asset.cleanup.enabled=false",
                "--app.room.game-timer.initial-delay-ms=3600000",
                "--app.asset.s3.bucket=load-test",
                "--app.asset.s3.public-base-url=https://cdn.example.com",
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN"
        );
        processBuilder.environment().put("LOAD_WORKER_READY_FILE", readyFile.toString());
        processBuilder.environment().put("LOAD_WORKER_START_FILE", startFile.toString());
        processBuilder.environment().put("LOAD_WORKER_CONCURRENCY", String.valueOf(workerConcurrency));
        processBuilder.environment().put("LOAD_WORKER_METRICS_FILE", metricsFile.toString());
        if (mode == WorkerMode.BLOCK_DURING_EXTRACTION) {
            processBuilder.environment().put("LOAD_WORKER_PROCESSING_FILE", processingFile.toString());
            processBuilder.environment().put("LOAD_WORKER_RELEASE_FILE", releaseFile.toString());
        }
        if (mode == WorkerMode.RECOVER_STALE) {
            processBuilder.environment().put("LOAD_WORKER_RECOVER_STALE", "true");
        }
        if (mode == WorkerMode.BENCHMARK) {
            processBuilder.environment().put(
                    "LOAD_WORKER_EXTRACTION_DELAY_MS",
                    String.valueOf(BENCHMARK_EXTRACTION_DELAY_MS)
            );
        }
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(logFile.toFile());
        return new WorkerProcess(processBuilder.start(), readyFile, processingFile, logFile, metricsFile);
    }

    private void awaitWorkerReady(WorkerProcess worker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!Files.exists(worker.readyFile())) {
            if (!worker.process().isAlive()) {
                throw new AssertionError("Worker exited before ready:\n" + readLog(worker.logFile()));
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Worker did not become ready:\n" + readLog(worker.logFile()));
            }
            Thread.sleep(50);
        }
    }

    private void assertWorkerCompleted(WorkerProcess worker) throws Exception {
        boolean completed = worker.process().waitFor(120, TimeUnit.SECONDS);
        assertThat(completed)
                .withFailMessage("Worker did not stop in time:\n%s", readLog(worker.logFile()))
                .isTrue();
        assertThat(worker.process().exitValue())
                .withFailMessage("Worker failed:\n%s", readLog(worker.logFile()))
                .isZero();
    }

    private void awaitWorkerProcessing(WorkerProcess worker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!Files.exists(worker.processingFile())) {
            if (!worker.process().isAlive()) {
                throw new AssertionError("Worker exited before processing:\n" + readLog(worker.logFile()));
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Worker did not start processing:\n" + readLog(worker.logFile()));
            }
            Thread.sleep(50);
        }
    }

    private Long findSingleProcessingJob(LoadFixture fixture) {
        List<AudioProcessingJob> processingJobs = jobRepository.findAllById(fixture.jobIds()).stream()
                .filter(job -> job.getStatus() == AudioProcessingJobStatus.PROCESSING)
                .toList();
        assertThat(processingJobs).hasSize(1);
        return processingJobs.getFirst().getId();
    }

    private void makeJobStale(Long jobId) {
        transactionTemplate.executeWithoutResult(status -> {
            AudioProcessingJob job = jobRepository.findByIdForUpdate(jobId).orElseThrow();
            ReflectionTestUtils.setField(job, "startedAt", LocalDateTime.now().minusMinutes(20));
            ReflectionTestUtils.setField(job, "heartbeatAt", LocalDateTime.now().minusMinutes(20));
        });
    }

    private int readProcessedCount(Path logFile) throws Exception {
        return Files.readAllLines(logFile, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith(WORKER_RESULT_PREFIX))
                .map(line -> line.substring(WORKER_RESULT_PREFIX.length()))
                .mapToInt(Integer::parseInt)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Worker result missing:\n" + readLog(logFile)));
    }

    private void readWorkerMetrics(
            Path metricsFile,
            List<Long> queueWaitMillis,
            List<Long> claimMicros
    ) throws Exception {
        for (String line : Files.readAllLines(metricsFile, StandardCharsets.UTF_8)) {
            String[] values = line.split(",");
            queueWaitMillis.add(Long.parseLong(values[0]));
            claimMicros.add(Long.parseLong(values[1]));
        }
    }

    private long percentile(List<Long> values, int percentile) {
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0));
    }

    private String readLog(Path logFile) {
        try {
            return Files.exists(logFile) ? Files.readString(logFile) : "";
        } catch (Exception exception) {
            return "Failed to read worker log: " + exception.getMessage();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeExternalServices {

        @Bean
        @Primary
        FakeAudioExtractionCommandRunner fakeAudioExtractionCommandRunner() {
            return new FakeAudioExtractionCommandRunner();
        }

        @Bean
        @Primary
        S3Client loadTestS3Client() {
            S3Client client = mock(S3Client.class);
            doAnswer(invocation -> PutObjectResponse.builder().build())
                    .when(client)
                    .putObject(any(PutObjectRequest.class), any(RequestBody.class));
            return client;
        }
    }

    static class FakeAudioExtractionCommandRunner implements AudioExtractionCommandRunner {

        private final AtomicInteger invocationCount = new AtomicInteger();
        private final Set<String> uniqueOutputs = ConcurrentHashMap.newKeySet();

        @Override
        public void extractYoutubeSegment(YoutubeAudioExtractionCommand command) throws java.io.IOException {
            invocationCount.incrementAndGet();
            uniqueOutputs.add(command.outputFile().getFileName().toString());
            Files.writeString(command.outputFile(), "test-audio");
        }

        int getInvocationCount() {
            return invocationCount.get();
        }

        int getUniqueOutputCount() {
            return uniqueOutputs.size();
        }
    }

    private record LoadFixture(List<Long> mapIds, List<Long> jobIds) {
    }

    private enum WorkerMode {
        NORMAL,
        BLOCK_DURING_EXTRACTION,
        RECOVER_STALE,
        BENCHMARK
    }

    private record WorkerProcess(
            Process process,
            Path readyFile,
            Path processingFile,
            Path logFile,
            Path metricsFile
    ) {
    }

    private record BenchmarkScenario(int serverCount, int workerConcurrency) {
    }

    private record BenchmarkResult(
            BenchmarkScenario scenario,
            long elapsedMillis,
            double jobsPerSecond,
            long queueWaitP50Millis,
            long queueWaitP95Millis,
            long claimP50Micros,
            long claimP95Micros
    ) {
        String toCsv() {
            return "%d,%d,%d,%d,%.2f,%d,%d,%d,%d".formatted(
                    scenario.serverCount(),
                    scenario.workerConcurrency(),
                    scenario.serverCount() * scenario.workerConcurrency(),
                    elapsedMillis,
                    jobsPerSecond,
                    queueWaitP50Millis,
                    queueWaitP95Millis,
                    claimP50Micros,
                    claimP95Micros
            );
        }
    }
}
