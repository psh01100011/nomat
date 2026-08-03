package com.dogdog.nomat.domain.game.entity;

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
        name = "game_player_results",
        indexes = {
                @Index(name = "idx_game_player_results_user_created_at", columnList = "user_id, created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_game_player_results_session_user",
                        columnNames = {"game_session_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GamePlayerResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSession gameSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
