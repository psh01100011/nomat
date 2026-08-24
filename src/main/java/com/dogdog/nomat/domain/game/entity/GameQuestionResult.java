package com.dogdog.nomat.domain.game.entity;

import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
        name = "game_question_results",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_game_question_results_session_number",
                        columnNames = {"game_session_id", "question_number"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameQuestionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSession gameSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "question_number", nullable = false)
    private int questionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_user_id")
    private User winnerUser;

    @Column(name = "winner_answer")
    private String winnerAnswer;

    @Column(name = "earned_score", nullable = false)
    private int earnedScore;

    @Column(name = "answered_ms")
    private Integer answeredMs;

    @Column(name = "ended_reason", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private GameQuestionEndedReason endedReason;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    private GameQuestionResult(
            GameSession gameSession,
            Question question,
            int questionNumber,
            User winnerUser,
            String winnerAnswer,
            int earnedScore,
            Integer answeredMs,
            GameQuestionEndedReason endedReason,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        this.gameSession = gameSession;
        this.question = question;
        this.questionNumber = questionNumber;
        this.winnerUser = winnerUser;
        this.winnerAnswer = winnerAnswer;
        this.earnedScore = earnedScore;
        this.answeredMs = answeredMs;
        this.endedReason = endedReason;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public static GameQuestionResult create(
            GameSession gameSession,
            Question question,
            int questionNumber,
            User winnerUser,
            String winnerAnswer,
            int earnedScore,
            Integer answeredMs,
            GameQuestionEndedReason endedReason,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        return new GameQuestionResult(
                gameSession,
                question,
                questionNumber,
                winnerUser,
                winnerAnswer,
                earnedScore,
                answeredMs,
                endedReason,
                startedAt,
                endedAt
        );
    }
}
