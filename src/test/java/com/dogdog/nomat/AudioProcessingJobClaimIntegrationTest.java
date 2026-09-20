package com.dogdog.nomat;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.dogdog.nomat.domain.map.service.AudioProcessingJobService;
import com.dogdog.nomat.domain.map.service.AudioProcessingTask;
import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

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

    private List<Long> saveQueuedJobs() {
        return transactionTemplate.execute(status -> {
            User creator = User.create("claim-test-user", "encoded-password", "claim-test-nickname");
            Category category = Category.create("claim-test-category");
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
                    2,
                    MapStatus.PROCESSING
            );
            entityManager.persist(map);
            AudioProcessingJob first = saveQueuedJob(map, 1);
            AudioProcessingJob second = saveQueuedJob(map, 2);
            entityManager.flush();
            return List.of(first.getId(), second.getId());
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
}
