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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
    private EntityManager entityManager;

    @Test
    void concurrentWorkersClaimJobOnlyOnce() throws Exception {
        Long jobId = saveQueuedJob();
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<AudioProcessingTask> first = executor.submit(() -> transactionTemplate.execute(status -> {
                AudioProcessingTask task = audioProcessingJobService.startJob(jobId);
                firstClaimed.countDown();
                await(releaseFirstTransaction);
                return task;
            }));
            assertThat(firstClaimed.await(5, TimeUnit.SECONDS)).isTrue();

            Future<AudioProcessingTask> second = executor.submit(() -> {
                secondStarted.countDown();
                return audioProcessingJobService.startJob(jobId);
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(second.isDone()).isFalse();

            releaseFirstTransaction.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(second.get(5, TimeUnit.SECONDS)).isNull();
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }

        AudioProcessingJob claimedJob = audioProcessingJobRepository.findById(jobId).orElseThrow();
        assertThat(claimedJob.getStatus()).isEqualTo(AudioProcessingJobStatus.PROCESSING);
        assertThat(claimedJob.getAttemptCount()).isEqualTo(1);
    }

    private Long saveQueuedJob() {
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
                    1,
                    MapStatus.PROCESSING
            );
            entityManager.persist(map);
            Question question = Question.create(map, 1, "이 노래는?");
            entityManager.persist(question);
            QuestionMedia media = QuestionMedia.create(
                    question,
                    null,
                    QuestionMediaSourceType.YOUTUBE,
                    "https://youtube.com/watch?v=claim-test",
                    0,
                    30_000,
                    30_000
            );
            entityManager.persist(media);
            AudioProcessingJob job = AudioProcessingJob.create(media);
            entityManager.persist(job);
            entityManager.flush();
            return job.getId();
        });
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
