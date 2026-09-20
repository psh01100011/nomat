package com.dogdog.nomat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.domain.game.entity.GameSession;
import com.dogdog.nomat.domain.game.entity.GameSessionEndedReason;
import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.game.repository.GameSessionRepository;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.audio-processing.enabled=false",
        "app.asset.cleanup.enabled=false",
        "app.room.game-timer.initial-delay-ms=600000"
})
class GameSessionSourceRoomConcurrencyIntegrationTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private QuizMapRepository quizMapRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void concurrentResultsForTheSameRoomIncrementPlayCountOnlyOnce() throws Exception {
        Long mapId = saveMap();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> saveGameResult(mapId, ready, start)),
                    executor.submit(() -> saveGameResult(mapId, ready, start))
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            assertThat(successCount).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(gameSessionRepository.count()).isEqualTo(1);
        long playCount = transactionTemplate.execute(status -> {
            entityManager.clear();
            return quizMapRepository.findById(mapId).orElseThrow().getPlayCount();
        });
        assertThat(playCount).isEqualTo(1);
    }

    private boolean saveGameResult(
            Long mapId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                int currentAttempt = attempt;
                return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                    if (gameSessionRepository.existsBySourceRoomId(25L)) {
                        return false;
                    }
                    QuizMap map = quizMapRepository.findById(mapId).orElseThrow();
                    if (currentAttempt == 1) {
                        ready.countDown();
                        await(start);
                    }
                    gameSessionRepository.saveAndFlush(GameSession.complete(
                            25L,
                            map,
                            map.getVersion(),
                            "방",
                            false,
                            10,
                            2,
                            1,
                            "seed",
                            30,
                            TimeLimitMode.FIXED,
                            true,
                            true,
                            10,
                            GameSessionEndedReason.COMPLETED,
                            null,
                            LocalDateTime.now().minusMinutes(1),
                            LocalDateTime.now()
                    ));
                    map.increasePlayCount();
                    return true;
                }));
            } catch (DataIntegrityViolationException exception) {
                return false;
            } catch (CannotAcquireLockException exception) {
                if (attempt == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Unreachable game result test state.");
    }

    private Long saveMap() {
        return transactionTemplate.execute(status -> {
            User creator = User.create("game-result-user", "encoded-password", "game-result-user");
            Category category = Category.create("game-result-category");
            entityManager.persist(creator);
            entityManager.persist(category);
            QuizMap map = QuizMap.create(
                    creator,
                    category,
                    null,
                    QuestionType.TEXT,
                    "게임 결과 경합 테스트",
                    "설명",
                    MapVisibility.PRIVATE,
                    1,
                    MapStatus.PUBLISHED
            );
            entityManager.persist(map);
            entityManager.flush();
            return map.getId();
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating game result transactions.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating game result transactions.", exception);
        }
    }
}
