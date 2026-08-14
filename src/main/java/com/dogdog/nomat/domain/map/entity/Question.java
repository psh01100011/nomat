package com.dogdog.nomat.domain.map.entity;

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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "questions",
        indexes = {
                @Index(name = "idx_questions_map_status", columnList = "map_id, status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_questions_map_order", columnNames = {"map_id", "question_order"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", nullable = false)
    private QuizMap map;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Lob
    @Column(name = "prompt_text")
    private String promptText;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionStatus status = QuestionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Question(QuizMap map, int questionOrder, String promptText) {
        this.map = map;
        this.questionOrder = questionOrder;
        this.promptText = promptText;
    }

    public static Question create(QuizMap map, int questionOrder, String promptText) {
        return new Question(map, questionOrder, promptText);
    }

    public void update(String promptText) {
        this.promptText = promptText;
    }

    public void delete() {
        if (status == QuestionStatus.DELETED) {
            return;
        }

        this.status = QuestionStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
