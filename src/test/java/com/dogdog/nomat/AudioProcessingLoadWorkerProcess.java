package com.dogdog.nomat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.dogdog.nomat.domain.map.service.AudioExtractionCommandRunner;
import com.dogdog.nomat.domain.map.service.AudioProcessingJobService;
import com.dogdog.nomat.domain.map.service.AudioProcessingService;
import com.dogdog.nomat.domain.map.service.AudioProcessingTask;
import com.dogdog.nomat.domain.map.service.YoutubeAudioExtractionCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public final class AudioProcessingLoadWorkerProcess {

    private static final Duration START_TIMEOUT = Duration.ofSeconds(60);

    private AudioProcessingLoadWorkerProcess() {
    }

    public static void main(String[] args) throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                NomatApplication.class,
                FakeWorkerExternalServices.class
        ).web(WebApplicationType.SERVLET).run(args)) {
            Path readyFile = Path.of(requiredEnvironment("LOAD_WORKER_READY_FILE"));
            Path startFile = Path.of(requiredEnvironment("LOAD_WORKER_START_FILE"));
            Files.writeString(readyFile, "ready");
            awaitStart(startFile);

            AudioProcessingJobService jobService = context.getBean(AudioProcessingJobService.class);
            AudioProcessingService processingService = context.getBean(AudioProcessingService.class);
            if (Boolean.parseBoolean(System.getenv("LOAD_WORKER_RECOVER_STALE"))) {
                processingService.recoverStaleJobs();
            }
            WorkerMetrics metrics = processUntilQueueIsEmpty(
                    jobService,
                    processingService,
                    configuredConcurrency()
            );
            writeMetrics(metrics);
            int processedCount = metrics.processedCount();
            System.out.println("AUDIO_LOAD_WORKER_RESULT processed=" + processedCount);
        }
    }

    private static WorkerMetrics processUntilQueueIsEmpty(
            AudioProcessingJobService jobService,
            AudioProcessingService processingService,
            int concurrency
    ) throws Exception {
        AtomicInteger processedCount = new AtomicInteger();
        ConcurrentLinkedQueue<Long> queueWaitMillis = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> claimMicros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<?>> workers = new ArrayList<>(concurrency);
            for (int index = 0; index < concurrency; index++) {
                workers.add(executor.submit(() -> processWorkerLoop(
                        jobService,
                        processingService,
                        processedCount,
                        queueWaitMillis,
                        claimMicros
                )));
            }
            for (Future<?> worker : workers) {
                worker.get();
            }
        } finally {
            executor.shutdownNow();
        }
        return new WorkerMetrics(processedCount.get(), queueWaitMillis, claimMicros);
    }

    private static void processWorkerLoop(
            AudioProcessingJobService jobService,
            AudioProcessingService processingService,
            AtomicInteger processedCount,
            ConcurrentLinkedQueue<Long> queueWaitMillis,
            ConcurrentLinkedQueue<Long> claimMicros
    ) {
        while (true) {
            long claimStartedAt = System.nanoTime();
            List<AudioProcessingTask> tasks = jobService.claimProcessableJobs(1);
            long claimDurationMicros = (System.nanoTime() - claimStartedAt) / 1_000;
            if (tasks.isEmpty()) {
                return;
            }
            AudioProcessingTask task = tasks.getFirst();
            claimMicros.add(claimDurationMicros);
            queueWaitMillis.add(task.queueWaitMs());
            processingService.processClaimedJob(task);
            processedCount.incrementAndGet();
        }
    }

    private static int configuredConcurrency() {
        String value = System.getenv("LOAD_WORKER_CONCURRENCY");
        return value == null || value.isBlank() ? 1 : Integer.parseInt(value);
    }

    private static void writeMetrics(WorkerMetrics metrics) throws java.io.IOException {
        String metricsFile = System.getenv("LOAD_WORKER_METRICS_FILE");
        if (metricsFile == null || metricsFile.isBlank()) {
            return;
        }

        List<Long> queueWaits = List.copyOf(metrics.queueWaitMillis());
        List<Long> claimDurations = List.copyOf(metrics.claimMicros());
        StringBuilder contents = new StringBuilder();
        for (int index = 0; index < queueWaits.size(); index++) {
            contents.append(queueWaits.get(index))
                    .append(',')
                    .append(claimDurations.get(index))
                    .append('\n');
        }
        Files.writeString(Path.of(metricsFile), contents);
    }

    private static void awaitStart(Path startFile) throws Exception {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (!Files.exists(startFile)) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Timed out waiting for the load test start signal.");
            }
            Thread.sleep(50);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured.");
        }
        return value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class FakeWorkerExternalServices {

        @Bean
        @Primary
        AudioExtractionCommandRunner fakeWorkerAudioExtractionCommandRunner() {
            return new AudioExtractionCommandRunner() {
                @Override
                public void extractYoutubeSegment(YoutubeAudioExtractionCommand command) throws java.io.IOException {
                    signalProcessingStarted();
                    awaitReleaseIfConfigured();
                    delayIfConfigured();
                    Files.writeString(command.outputFile(), "test-audio");
                }
            };
        }

        private void delayIfConfigured() throws java.io.IOException {
            String delayMillis = System.getenv("LOAD_WORKER_EXTRACTION_DELAY_MS");
            if (delayMillis == null || delayMillis.isBlank()) {
                return;
            }
            try {
                Thread.sleep(Long.parseLong(delayMillis));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("Interrupted during test extraction delay.", exception);
            }
        }

        private void signalProcessingStarted() throws java.io.IOException {
            String signalFile = System.getenv("LOAD_WORKER_PROCESSING_FILE");
            if (signalFile != null && !signalFile.isBlank()) {
                Files.writeString(Path.of(signalFile), "processing");
            }
        }

        private void awaitReleaseIfConfigured() throws java.io.IOException {
            String releaseFile = System.getenv("LOAD_WORKER_RELEASE_FILE");
            if (releaseFile == null || releaseFile.isBlank()) {
                return;
            }

            Path path = Path.of(releaseFile);
            while (!Files.exists(path)) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException("Interrupted while waiting for test release.", exception);
                }
            }
        }

        @Bean
        @Primary
        S3Client fakeWorkerS3Client() {
            S3Client client = mock(S3Client.class);
            doAnswer(invocation -> PutObjectResponse.builder().build())
                    .when(client)
                    .putObject(any(PutObjectRequest.class), any(RequestBody.class));
            return client;
        }
    }

    private record WorkerMetrics(
            int processedCount,
            ConcurrentLinkedQueue<Long> queueWaitMillis,
            ConcurrentLinkedQueue<Long> claimMicros
    ) {
    }
}
