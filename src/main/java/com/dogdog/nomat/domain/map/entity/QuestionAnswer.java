package com.dogdog.nomat.domain.map.entity;

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
        name = "question_answers",
        indexes = {
                @Index(name = "idx_question_answers_question_primary", columnList = "question_id, is_primary")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_question_answers_question_key", columnNames = {"question_id", "answer_key"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "answer_text", nullable = false)
    private String answerText;

    @Column(name = "answer_key", nullable = false)
    private String answerKey;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
