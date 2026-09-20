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
        name = "audio_processing_jobs",
        indexes = {
                @Index(name = "idx_audio_processing_jobs_status_created_at", columnList = "status, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AudioProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_media_id", nullable = false, unique = true)
    private QuestionMedia questionMedia;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private AudioProcessingJobStatus status = AudioProcessingJobStatus.QUEUED;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "failure_code", length = 40)
    @Enumerated(EnumType.STRING)
    private AudioProcessingFailureCode failureCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private AudioProcessingJob(QuestionMedia questionMedia) {
        this.questionMedia = questionMedia;
    }

    public static AudioProcessingJob create(QuestionMedia questionMedia) {
        return new AudioProcessingJob(questionMedia);
    }

    public void start() {
        this.status = AudioProcessingJobStatus.PROCESSING;
        this.attemptCount++;
        this.startedAt = LocalDateTime.now();
        this.failureMessage = null;
        this.failureCode = null;
        this.questionMedia.startProcessing();
    }

    public void reset() {
        resetState();
        this.questionMedia.resetProcessing();
    }

    public void retryManually(LocalDateTime requestedAt) {
        resetState();
        this.questionMedia.requestReprocessing(requestedAt);
    }

    private void resetState() {
        this.status = AudioProcessingJobStatus.QUEUED;
        this.attemptCount = 0;
        this.failureMessage = null;
        this.failureCode = null;
        this.startedAt = null;
        this.completedAt = null;
    }

    public void scheduleRetry(AudioProcessingFailureCode failureCode, String failureMessage) {
        this.status = AudioProcessingJobStatus.RETRYING;
        this.failureCode = failureCode;
        this.failureMessage = truncate(failureMessage);
        this.startedAt = null;
        this.completedAt = null;
        this.questionMedia.retryProcessing();
    }

    public boolean canAutomaticallyRetry(int maxRetryAttempts) {
        return attemptCount < maxRetryAttempts;
    }

    public boolean canRetryManually() {
        return status == AudioProcessingJobStatus.FAILED
                && failureCode != null
                && failureCode.isManualRetryAllowed();
    }

    public void succeed() {
        this.status = AudioProcessingJobStatus.SUCCEEDED;
        this.completedAt = LocalDateTime.now();
        this.failureMessage = null;
        this.failureCode = null;
    }

    public void fail(AudioProcessingFailureCode failureCode, String failureMessage) {
        this.status = AudioProcessingJobStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.failureCode = failureCode;
        this.failureMessage = truncate(failureMessage);
        this.questionMedia.failProcessing(failureCode);
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

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }

        return value.substring(0, 500);
    }
}
