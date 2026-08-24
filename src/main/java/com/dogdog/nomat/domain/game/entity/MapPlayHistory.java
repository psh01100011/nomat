package com.dogdog.nomat.domain.game.entity;

import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "map_play_histories",
        indexes = {
                @Index(name = "idx_map_play_histories_map_id", columnList = "map_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_map_play_histories_user_map", columnNames = {"user_id", "map_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapPlayHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", nullable = false)
    private QuizMap map;

    @Column(name = "play_count", nullable = false)
    private int playCount;

    @Column(name = "best_score", nullable = false)
    private int bestScore;

    @Column(name = "last_score", nullable = false)
    private int lastScore;

    @Column(name = "first_played_at")
    private LocalDateTime firstPlayedAt;

    @Column(name = "last_played_at")
    private LocalDateTime lastPlayedAt;

    private MapPlayHistory(User user, QuizMap map, int score, LocalDateTime playedAt) {
        this.user = user;
        this.map = map;
        this.playCount = 1;
        this.bestScore = score;
        this.lastScore = score;
        this.firstPlayedAt = playedAt;
        this.lastPlayedAt = playedAt;
    }

    public static MapPlayHistory create(User user, QuizMap map, int score, LocalDateTime playedAt) {
        return new MapPlayHistory(user, map, score, playedAt);
    }

    public void recordPlay(int score, LocalDateTime playedAt) {
        this.playCount++;
        this.lastScore = score;
        this.bestScore = Math.max(bestScore, score);
        if (firstPlayedAt == null) {
            this.firstPlayedAt = playedAt;
        }
        this.lastPlayedAt = playedAt;
    }
}
