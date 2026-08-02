package com.dogdog.nomat.domain.game.entity;

import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "game_sessions",
        indexes = {
                @Index(name = "idx_game_sessions_map_started_at", columnList = "map_id, started_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", nullable = false)
    private QuizMap map;

    @Column(name = "map_version", nullable = false)
    private int mapVersion;

    @Column(name = "room_title", length = 100)
    private String roomTitle;

    @Column(name = "room_has_password", nullable = false)
    private boolean roomHasPassword;

    @Column(name = "invite_code", length = 30)
    private String inviteCode;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Column(name = "selected_question_count", nullable = false)
    private int selectedQuestionCount;

    @Column(name = "random_seed", length = 100)
    private String randomSeed;

    @Column(name = "answer_time_limit_seconds", nullable = false)
    private int answerTimeLimitSeconds;

    @Column(name = "time_limit_mode", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private TimeLimitMode timeLimitMode = TimeLimitMode.FIXED;

    @Column(name = "audio_repeat_enabled", nullable = false)
    private boolean audioRepeatEnabled = true;

    @Column(name = "initial_hint_enabled", nullable = false)
    private boolean initialHintEnabled = true;

    @Column(name = "initial_hint_trigger_seconds", nullable = false)
    private int initialHintTriggerSeconds = 10;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private GameSessionStatus status = GameSessionStatus.COMPLETED;

    @Column(name = "ended_reason", length = 30)
    @Enumerated(EnumType.STRING)
    private GameSessionEndedReason endedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ended_by_user_id")
    private User endedByUser;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
