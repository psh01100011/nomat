package com.dogdog.nomat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.game.entity.MapPlayHistory;
import com.dogdog.nomat.domain.game.repository.MapPlayHistoryRepository;
import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.comment.entity.MapCommentStatus;
import com.dogdog.nomat.domain.comment.repository.MapCommentRepository;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.MapLikeRepository;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.map.service.AdminMapCounterService;
import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
        "app.asset.cleanup.enabled=false",
        "app.room.game-timer.initial-delay-ms=600000"
})
class MapConcurrencyIntegrationTest {

    @Autowired
    private QuizMapRepository quizMapRepository;

    @Autowired
    private MapLikeRepository mapLikeRepository;

    @Autowired
    private MapPlayHistoryRepository mapPlayHistoryRepository;

    @Autowired
    private AdminMapCounterService adminMapCounterService;

    @Autowired
    private MapCommentRepository mapCommentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void concurrentLikeRequestsKeepRelationAndCounterInSync() throws Exception {
        TestData testData = saveMapAndUsers(12);
        List<Long> requestedUserIds = new ArrayList<>(testData.userIds());
        requestedUserIds.add(testData.userIds().getFirst());

        runRunnablesConcurrently(requestedUserIds.stream()
                .<Runnable>map(userId -> () -> transactionTemplate.executeWithoutResult(status -> {
                    quizMapRepository.incrementLikeCount(testData.mapId());
                    if (mapLikeRepository.insertIfAbsent(testData.mapId(), userId) == 0) {
                        quizMapRepository.decrementLikeCount(testData.mapId());
                    }
                }))
                .toList());

        transactionTemplate.executeWithoutResult(status -> entityManager.clear());
        QuizMap map = quizMapRepository.findById(testData.mapId()).orElseThrow();
        assertThat(mapLikeRepository.countByMapId(testData.mapId())).isEqualTo(12);
        assertThat(map.getLikeCount()).isEqualTo(12);
    }

    @Test
    void onlyOneConcurrentRequestCanClaimTheSameMapVersion() throws Exception {
        Long mapId = saveMapAndUsers(0).mapId();
        List<Integer> claimedRows = runConcurrently(List.of(
                () -> quizMapRepository.claimVersion(mapId, 1),
                () -> quizMapRepository.claimVersion(mapId, 1)
        ));

        assertThat(claimedRows).containsExactlyInAnyOrder(0, 1);
        transactionTemplate.executeWithoutResult(status -> entityManager.clear());
        assertThat(quizMapRepository.findById(mapId).orElseThrow().getVersion()).isEqualTo(2);
    }

    @Test
    void concurrentPlayResultsKeepPlayCountAndBestScore() throws Exception {
        TestData testData = saveMapAndUsers(1);
        Long userId = testData.userIds().getFirst();

        runConcurrently(List.of(
                () -> recordPlay(testData.mapId(), userId, 10, 2),
                () -> recordPlay(testData.mapId(), userId, 30, 3)
        ));

        transactionTemplate.executeWithoutResult(status -> entityManager.clear());
        MapPlayHistory history = mapPlayHistoryRepository
                .findByUserIdAndMapId(userId, testData.mapId())
                .orElseThrow();
        assertThat(history.getPlayCount()).isEqualTo(2);
        assertThat(history.getBestScore()).isEqualTo(30);
        assertThat(quizMapRepository.findById(testData.mapId()).orElseThrow().getPlayCount()).isEqualTo(5);
    }

    @Test
    void reconciliationRepairsStoredCounterFromRelationTable() {
        TestData testData = saveMapAndUsers(1);
        transactionTemplate.executeWithoutResult(status ->
                mapLikeRepository.insertIfAbsent(testData.mapId(), testData.userIds().getFirst())
        );

        assertThat(adminMapCounterService.check(testData.mapId()).consistent()).isFalse();
        assertThat(adminMapCounterService.reconcile(testData.mapId()).repaired()).isTrue();

        transactionTemplate.executeWithoutResult(status -> entityManager.clear());
        assertThat(quizMapRepository.findById(testData.mapId()).orElseThrow().getLikeCount()).isEqualTo(1);
        assertThat(adminMapCounterService.check(testData.mapId()).consistent()).isTrue();
    }

    @Test
    void concurrentDeletesDecreaseCommentCountOnlyOnce() throws Exception {
        TestData testData = saveMapAndUsers(1);
        Long commentId = saveComment(testData.mapId(), testData.userIds().getFirst());

        runConcurrently(List.of(
                () -> deleteComment(testData.mapId(), commentId),
                () -> deleteComment(testData.mapId(), commentId)
        ));

        transactionTemplate.executeWithoutResult(status -> entityManager.clear());
        assertThat(mapCommentRepository.countByMapIdAndStatus(testData.mapId(), MapCommentStatus.ACTIVE))
                .isZero();
        assertThat(quizMapRepository.findById(testData.mapId()).orElseThrow().getCommentCount()).isZero();
    }

    private int recordPlay(Long mapId, Long userId, int score, int playerCount) {
        quizMapRepository.addPlayCount(mapId, playerCount);
        return mapPlayHistoryRepository.recordPlay(userId, mapId, score, java.time.LocalDateTime.now());
    }

    private Long saveComment(Long mapId, Long userId) {
        return transactionTemplate.execute(status -> {
            QuizMap map = entityManager.find(QuizMap.class, mapId);
            User user = entityManager.find(User.class, userId);
            quizMapRepository.incrementCommentCount(mapId);
            MapComment comment = MapComment.create(map, user, "동시 삭제 댓글");
            entityManager.persist(comment);
            entityManager.flush();
            return comment.getId();
        });
    }

    private int deleteComment(Long mapId, Long commentId) {
        int decrementedRows = quizMapRepository.decrementCommentCount(mapId);
        int deletedRows = mapCommentRepository.deleteIfActive(
                commentId,
                MapCommentStatus.ACTIVE,
                MapCommentStatus.DELETED,
                java.time.LocalDateTime.now()
        );
        if (deletedRows == 0 && decrementedRows > 0) {
            quizMapRepository.incrementCommentCount(mapId);
        }
        return deletedRows;
    }

    private TestData saveMapAndUsers(int userCount) {
        return transactionTemplate.execute(status -> {
            String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
            User creator = User.create(
                    "counter-creator-" + suffix,
                    "encoded-password",
                    "counter-creator-" + suffix
            );
            Category category = Category.create("counter-category");
            entityManager.persist(creator);
            entityManager.persist(category);
            QuizMap map = QuizMap.create(
                    creator,
                    category,
                    null,
                    QuestionType.TEXT,
                    "동시성 테스트 맵",
                    "설명",
                    MapVisibility.PUBLIC,
                    1,
                    MapStatus.PUBLISHED
            );
            entityManager.persist(map);

            List<Long> userIds = new ArrayList<>();
            for (int index = 0; index < userCount; index++) {
                User user = User.create(
                        "counter-user-" + suffix + "-" + index,
                        "encoded-password",
                        "counter-user-" + suffix + "-" + index
                );
                entityManager.persist(user);
                entityManager.flush();
                userIds.add(user.getId());
            }
            entityManager.flush();
            return new TestData(map.getId(), List.copyOf(userIds));
        });
    }

    private void runRunnablesConcurrently(List<Runnable> tasks) throws Exception {
        List<Supplier<Void>> suppliers = tasks.stream()
                .<Supplier<Void>>map(task -> () -> {
                    task.run();
                    return null;
                })
                .toList();
        runConcurrently(suppliers);
    }

    private <T> List<T> runConcurrently(List<Supplier<T>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());

        try {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        return transactionTemplate.execute(status -> task.get());
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating concurrent map requests.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent map requests.", exception);
        }
    }

    private record TestData(Long mapId, List<Long> userIds) {
    }
}
