package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import java.time.LocalDateTime;

public record AdminAudioProcessingJobResponse(
        Long jobId,
        Long mapId,
        Long questionId,
        Long questionMediaId,
        String status,
        int attemptCount,
        String failureType,
        String failureCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {

    public static AdminAudioProcessingJobResponse from(AudioProcessingJob job) {
        QuestionMedia media = job.getQuestionMedia();
        AudioProcessingFailureCode failureCode = job.getFailureCode();
        return new AdminAudioProcessingJobResponse(
                job.getId(),
                media.getQuestion().getMap().getId(),
                media.getQuestion().getId(),
                media.getId(),
                job.getStatus().name(),
                job.getAttemptCount(),
                failureCode == null ? null : failureCode.getFailureType().name(),
                failureCode == null ? null : failureCode.name(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }
}
