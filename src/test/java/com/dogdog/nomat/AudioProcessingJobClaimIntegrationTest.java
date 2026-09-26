package com.dogdog.nomat;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.dogdog.nomat.domain.map.service.AudioProcessingJobService;
import com.dogdog.nomat.domain.map.service.AudioProcessingTask;
import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.audio-processing.enabled=false",
        "app.asset.cleanup.enabled=false"
})
class AudioProcessingJobClaimIntegrationTest {

    @Autowired
    private AudioProcessingJobService audioProcessingJobService;

    @Autowired
    private AudioProcessingJobRepository audioProcessingJobRepository;

    @Autowired
    private QuizMapRepository quizMapRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void deleteJobs() {
        audioProcessingJobRepository.deleteAll();
    }

    @Test
    void concurrentWorkersClaimDifferentJobsWithoutWaitingForEachOther() throws Exception {
        List<Long> jobIds = saveQueuedJobs();
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate claimTransaction = new TransactionTemplate(transactionManager);
        claimTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        try {
            Future<AudioProcessingTask> first = executor.submit(() -> claimTransaction.execute(status -> {
                AudioProcessingTask task = audioProcessingJobService.claimProcessableJobs(1).getFirst();
                firstClaimed.countDown();
                await(releaseFirstTransaction);
                return task;
            }));
            assertThat(firstClaimed.await(5, TimeUnit.SECONDS)).isTrue();

            Future<AudioProcessingTask> second = executor.submit(() ->
                    audioProcessingJobService.claimProcessableJobs(1).getFirst()
            );
            AudioProcessingTask secondTask = second.get(5, TimeUnit.SECONDS);

            releaseFirstTransaction.countDown();

            AudioProcessingTask firstTask = first.get(5, TimeUnit.SECONDS);
            assertThat(firstTask.jobId()).isEqualTo(jobIds.get(0));
            assertThat(secondTask.jobId()).isEqualTo(jobIds.get(1));
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }

        List<AudioProcessingJob> claimedJobs = audioProcessingJobRepository.findAllById(jobIds);
        assertThat(claimedJobs)
                .allSatisfy(job -> {
                    assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.PROCESSING);
                    assertThat(job.getAttemptCount()).isEqualTo(1);
                });
    }

    @Test
    void concurrentServersRecoverDifferentStaleJobsWithoutWaitingForEachOther() throws Exception {
        List<Long> jobIds = saveStaleJobs();
        CountDownLatch firstRecovered = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate recoveryTransaction = new TransactionTemplate(transactionManager);
        recoveryTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        try {
            Future<Integer> first = executor.submit(() -> recoveryTransaction.execute(status -> {
                int count = audioProcessingJobService.recoverStaleProcessingJobs(1, 10, 3);
                firstRecovered.countDown();
                await(releaseFirstTransaction);
                return count;
            }));
            assertThat(firstRecovered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Integer> second = executor.submit(() ->
                    audioProcessingJobService.recoverStaleProcessingJobs(1, 10, 3)
            );
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(1);

            releaseFirstTransaction.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }

        List<AudioProcessingJob> recoveredJobs = audioProcessingJobRepository.findAllById(jobIds);
        assertThat(recoveredJobs)
                .allSatisfy(job -> {
                    assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.QUEUED);
                    assertThat(job.getStartedAt()).isNull();
                    assertThat(job.getAttemptCount()).isEqualTo(1);
                });
    }

    @Test
    void staleRecoveryRejectsLateCompletionFromTheOriginalServer() throws Exception {
        List<Long> jobIds = saveStaleJobs();
        long assetCountBefore = assetRepository.count();
        CountDownLatch staleRecovered = new CountDownLatch(1);
        CountDownLatch releaseRecoveryTransaction = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate recoveryTransaction = new TransactionTemplate(transactionManager);
        recoveryTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        try {
            Future<Integer> recovery = executor.submit(() -> recoveryTransaction.execute(status -> {
                int count = audioProcessingJobService.recoverStaleProcessingJobs(1, 10, 3);
                staleRecovered.countDown();
                await(releaseRecoveryTransaction);
                return count;
            }));
            assertThat(staleRecovered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> lateCompletion = executor.submit(() -> audioProcessingJobService.completeJob(
                    jobIds.getFirst(),
                    1,
                    "obsolete.mp3",
                    "uploads/audios/obsolete.mp3",
                    "https://cdn.example.com/uploads/audios/obsolete.mp3",
                    1234L,
                    30_000
            ));

            releaseRecoveryTransaction.countDown();

            assertThat(recovery.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(lateCompletion.get(5, TimeUnit.SECONDS)).isFalse();
        } finally {
            releaseRecoveryTransaction.countDown();
            executor.shutdownNow();
        }

        AudioProcessingJob recoveredJob = audioProcessingJobRepository.findById(jobIds.getFirst()).orElseThrow();
        assertThat(recoveredJob.getStatus()).isEqualTo(AudioProcessingJobStatus.QUEUED);
        assertThat(assetRepository.count()).isEqualTo(assetCountBefore);
    }

    @Test
    void concurrentLastJobsPublishTheirMapAfterBothComplete() throws Exception {
        ProcessingMapFixture fixture = saveProcessingMap();
        long assetCountBefore = assetRepository.count();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return completeJob(fixture.jobIds().get(0));
            });
            Future<Boolean> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return completeJob(fixture.jobIds().get(1));
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        QuizMap map = quizMapRepository.findById(fixture.mapId()).orElseThrow();
        assertThat(map.getStatus()).isEqualTo(MapStatus.PUBLISHED);
        assertThat(map.getPublishedAt()).isNotNull();
        assertThat(assetRepository.count()).isEqualTo(assetCountBefore + 2);
    }

    @Test
    void anotherWorkerRecoversAndCompletesAJobAbandonedByAStoppedWorker() {
        ProcessingMapFixture fixture = saveQueuedMap(1);
        Long jobId = fixture.jobIds().getFirst();
        long assetCountBefore = assetRepository.count();

        AudioProcessingTask abandonedTask = audioProcessingJobService
                .claimProcessableJobs(1)
                .getFirst();
        makeJobStale(jobId);

        assertThat(audioProcessingJobService.recoverStaleProcessingJobs(1, 10, 3)).isEqualTo(1);
        assertThat(completeJob(jobId, abandonedTask.attemptCount())).isFalse();

        AudioProcessingTask recoveredTask = audioProcessingJobService
                .claimProcessableJobs(1)
                .getFirst();
        assertThat(recoveredTask.jobId()).isEqualTo(jobId);
        assertThat(recoveredTask.attemptCount()).isEqualTo(2);
        assertThat(completeJob(jobId, recoveredTask.attemptCount())).isTrue();

        AudioProcessingJob completedJob = audioProcessingJobRepository.findById(jobId).orElseThrow();
        assertThat(completedJob.getStatus()).isEqualTo(AudioProcessingJobStatus.SUCCEEDED);
        assertThat(quizMapRepository.findById(fixture.mapId()).orElseThrow().getStatus())
                .isEqualTo(MapStatus.PUBLISHED);
        assertThat(assetRepository.count()).isEqualTo(assetCountBefore + 1);
    }

    private List<Long> saveQueuedJobs() {
        return saveQueuedMap(2).jobIds();
    }

    private ProcessingMapFixture saveQueuedMap(int questionCount) {
        return transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User creator = User.create("claim-" + suffix, "encoded-password", "nick-" + suffix);
            Category category = Category.create("category-" + suffix);
            entityManager.persist(creator);
            entityManager.persist(category);

            QuizMap map = QuizMap.create(
                    creator,
                    category,
                    null,
                    QuestionType.AUDIO,
                    "선점 경쟁 테스트",
                    "설명",
                    MapVisibility.PRIVATE,
                    questionCount,
                    MapStatus.PROCESSING
            );
            entityManager.persist(map);
            List<AudioProcessingJob> jobs = IntStream.rangeClosed(1, questionCount)
                    .mapToObj(sequence -> saveQueuedJob(map, sequence))
                    .toList();
            entityManager.flush();
            return new ProcessingMapFixture(
                    jobs.stream().map(AudioProcessingJob::getId).toList(),
                    map.getId()
            );
        });
    }

    private List<Long> saveStaleJobs() {
        return transactionTemplate.execute(status -> {
            List<Long> jobIds = saveQueuedJobs();
            LocalDateTime staleStartedAt = LocalDateTime.now().minusMinutes(20);
            audioProcessingJobRepository.findAllById(jobIds).forEach(job -> {
                job.start();
                ReflectionTestUtils.setField(job, "startedAt", staleStartedAt);
            });
            entityManager.flush();
            return jobIds;
        });
    }

    private ProcessingMapFixture saveProcessingMap() {
        return transactionTemplate.execute(status -> {
            List<Long> jobIds = saveQueuedJobs();
            List<AudioProcessingJob> jobs = audioProcessingJobRepository.findAllById(jobIds);
            jobs.forEach(AudioProcessingJob::start);
            Long mapId = jobs.getFirst().getQuestionMedia().getQuestion().getMap().getId();
            entityManager.flush();
            return new ProcessingMapFixture(jobIds, mapId);
        });
    }

    private boolean completeJob(Long jobId) {
        return completeJob(jobId, 1);
    }

    private boolean completeJob(Long jobId, int attemptCount) {
        return audioProcessingJobService.completeJob(
                jobId,
                attemptCount,
                "question-media-" + jobId + ".mp3",
                "uploads/audios/" + jobId + ".mp3",
                "https://cdn.example.com/uploads/audios/" + jobId + ".mp3",
                1234L,
                30_000
        );
    }

    private void makeJobStale(Long jobId) {
        transactionTemplate.executeWithoutResult(status -> {
            AudioProcessingJob job = audioProcessingJobRepository.findByIdForUpdate(jobId).orElseThrow();
            ReflectionTestUtils.setField(job, "startedAt", LocalDateTime.now().minusMinutes(20));
        });
    }

    private AudioProcessingJob saveQueuedJob(QuizMap map, int sequence) {
        Question question = Question.create(map, sequence, "이 노래는? " + sequence);
        entityManager.persist(question);
        QuestionMedia media = QuestionMedia.create(
                question,
                null,
                QuestionMediaSourceType.YOUTUBE,
                "https://youtube.com/watch?v=claim-test-" + sequence,
                0,
                30_000,
                30_000
        );
        entityManager.persist(media);
        AudioProcessingJob job = AudioProcessingJob.create(media);
        entityManager.persist(job);
        return job;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating claim transactions.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating claim transactions.", exception);
        }
    }

    private record ProcessingMapFixture(List<Long> jobIds, Long mapId) {
    }
}
