package com.dogdog.nomat.domain.game.repository;

import com.dogdog.nomat.domain.game.entity.MapPlayHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapPlayHistoryRepository extends JpaRepository<MapPlayHistory, Long> {

    Optional<MapPlayHistory> findByUserIdAndMapId(Long userId, Long mapId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO map_play_histories (
                user_id, map_id, play_count, best_score, last_score, first_played_at, last_played_at
            ) VALUES (
                :userId, :mapId, 1, :score, :score, :playedAt, :playedAt
            )
            ON DUPLICATE KEY UPDATE
                play_count = play_count + 1,
                best_score = GREATEST(best_score, :score),
                last_score = CASE
                    WHEN last_played_at IS NULL OR last_played_at <= :playedAt THEN :score
                    ELSE last_score
                END,
                first_played_at = CASE
                    WHEN first_played_at IS NULL OR first_played_at > :playedAt THEN :playedAt
                    ELSE first_played_at
                END,
                last_played_at = CASE
                    WHEN last_played_at IS NULL OR last_played_at < :playedAt THEN :playedAt
                    ELSE last_played_at
                END
            """, nativeQuery = true)
    int recordPlay(
            @Param("userId") Long userId,
            @Param("mapId") Long mapId,
            @Param("score") int score,
            @Param("playedAt") java.time.LocalDateTime playedAt
    );
}
