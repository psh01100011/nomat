package com.dogdog.nomat.domain.map.entity;

import com.dogdog.nomat.domain.asset.entity.Asset;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "question_media",
        indexes = {
                @Index(name = "idx_question_media_source_type", columnList = "source_type"),
                @Index(name = "idx_question_media_asset_id", columnList = "asset_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, unique = true)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "source_type", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionMediaSourceType sourceType;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "start_time_ms")
    private Integer startTimeMs;

    @Column(name = "end_time_ms")
    private Integer endTimeMs;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
