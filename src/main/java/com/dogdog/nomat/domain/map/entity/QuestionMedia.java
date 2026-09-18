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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

    @Column(name = "processing_status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionMediaProcessingStatus processingStatus = QuestionMediaProcessingStatus.READY;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "failure_code", length = 40)
    @Enumerated(EnumType.STRING)
    private AudioProcessingFailureCode failureCode;

    @Column(name = "processing_requested_at")
    private LocalDateTime processingRequestedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private QuestionMedia(
            Question question,
            Asset asset,
            QuestionMediaSourceType sourceType,
            String sourceUrl,
            Integer startTimeMs,
            Integer endTimeMs,
            Integer durationMs
    ) {
        this.question = question;
        this.asset = asset;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.durationMs = durationMs;
        if (sourceType == QuestionMediaSourceType.YOUTUBE || sourceType == QuestionMediaSourceType.TTS) {
            this.processingStatus = QuestionMediaProcessingStatus.QUEUED;
            this.processingRequestedAt = LocalDateTime.now();
        }
    }

    public static QuestionMedia create(
            Question question,
            Asset asset,
            QuestionMediaSourceType sourceType,
            String sourceUrl,
            Integer startTimeMs,
            Integer endTimeMs,
            Integer durationMs
    ) {
        return new QuestionMedia(question, asset, sourceType, sourceUrl, startTimeMs, endTimeMs, durationMs);
    }

    public void update(
            Asset asset,
            QuestionMediaSourceType sourceType,
            String sourceUrl,
            Integer startTimeMs,
            Integer endTimeMs,
            Integer durationMs
    ) {
        this.asset = asset;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.durationMs = durationMs;
        this.failureMessage = null;
        this.failureCode = null;
        if (sourceType == QuestionMediaSourceType.YOUTUBE || sourceType == QuestionMediaSourceType.TTS) {
            this.processingStatus = QuestionMediaProcessingStatus.QUEUED;
            this.processingRequestedAt = LocalDateTime.now();
        } else {
            this.processingStatus = QuestionMediaProcessingStatus.READY;
            this.processingRequestedAt = null;
        }
    }

    public void startProcessing() {
        this.processingStatus = QuestionMediaProcessingStatus.PROCESSING;
        this.failureMessage = null;
        this.failureCode = null;
    }

    public void resetProcessing() {
        this.processingStatus = QuestionMediaProcessingStatus.QUEUED;
        this.failureMessage = null;
        this.failureCode = null;
    }

    public void requestReprocessing(LocalDateTime requestedAt) {
        resetProcessing();
        this.processingRequestedAt = requestedAt;
    }

    public void retryProcessing() {
        this.processingStatus = QuestionMediaProcessingStatus.RETRYING;
        this.failureMessage = null;
        this.failureCode = null;
    }

    public void completeProcessing(Asset asset, Integer durationMs) {
        this.asset = asset;
        this.durationMs = durationMs;
        this.processingStatus = QuestionMediaProcessingStatus.READY;
        this.failureMessage = null;
        this.failureCode = null;
    }

    public void failProcessing(AudioProcessingFailureCode failureCode) {
        this.processingStatus = QuestionMediaProcessingStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureCode.getUserMessage();
    }

    public boolean canRetry() {
        return processingStatus == QuestionMediaProcessingStatus.FAILED
                && failureCode != null
                && failureCode.isManualRetryAllowed();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }

        return value.substring(0, 500);
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
