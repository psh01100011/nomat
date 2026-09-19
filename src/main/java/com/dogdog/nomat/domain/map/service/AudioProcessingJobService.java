package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaProcessingStatus;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import com.dogdog.nomat.domain.map.repository.QuestionMediaRepository;
import com.dogdog.nomat.domain.map.monitoring.AudioProcessingMetrics;
import com.dogdog.nomat.domain.user.entity.User;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioProcessingJobService {

    private final AudioProcessingJobRepository audioProcessingJobRepository;
    private final QuestionMediaRepository questionMediaRepository;
    private final AssetRepository assetRepository;
    private final AudioProcessingMetrics processingMetrics;

    @Transactional(readOnly = true)
    public List<Long> getProcessableJobIds(int batchSize) {
        return audioProcessingJobRepository
                .findByStatusInOrderByCreatedAtAsc(
                        List.of(AudioProcessingJobStatus.QUEUED, AudioProcessingJobStatus.RETRYING),
                        PageRequest.of(0, batchSize)
                )
                .stream()
                .map(AudioProcessingJob::getId)
                .toList();
    }

    @Transactional
    public int recoverStaleProcessingJobs(int batchSize, long processingTimeoutMinutes, int maxRetryAttempts) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(processingTimeoutMinutes);
        List<AudioProcessingJob> jobs = audioProcessingJobRepository
                .findByStatusAndStartedAtBeforeOrderByStartedAtAsc(
                        AudioProcessingJobStatus.PROCESSING,
                        threshold,
                        PageRequest.of(0, batchSize)
                );
        jobs.forEach(job -> {
            retryOrFail(job, maxRetryAttempts);
            processingMetrics.recordStaleRecovery(job.getStatus());
            log.warn(
                    "event=audio_job_stale_recovered jobId={} mapId={} status={} attempt={} failureCode={}",
                    job.getId(),
                    job.getQuestionMedia().getQuestion().getMap().getId(),
                    job.getStatus(),
                    job.getAttemptCount(),
                    job.getFailureCode()
            );
        });
        return jobs.size();
    }

    @Transactional
    public AudioProcessingTask startJob(Long jobId) {
        AudioProcessingJob job = audioProcessingJobRepository.findByIdForUpdate(jobId)
                .filter(foundJob -> foundJob.getStatus() == AudioProcessingJobStatus.QUEUED
                        || foundJob.getStatus() == AudioProcessingJobStatus.RETRYING)
                .orElse(null);
        if (job == null) {
            return null;
        }

        job.start();
        QuestionMedia media = job.getQuestionMedia();
        Long mapId = media.getQuestion().getMap().getId();
        long queueWaitMs = job.getCreatedAt() == null
                ? 0
                : Duration.between(job.getCreatedAt(), job.getStartedAt()).toMillis();
        log.info(
                "event=audio_job_claimed jobId={} mapId={} questionId={} mediaId={} attempt={} queueWaitMs={}",
                job.getId(), mapId, media.getQuestion().getId(), media.getId(), job.getAttemptCount(), queueWaitMs
        );
        return new AudioProcessingTask(
                job.getId(),
                mapId,
                media.getQuestion().getId(),
                media.getId(),
                job.getAttemptCount(),
                queueWaitMs,
                media.getSourceUrl(),
                media.getStartTimeMs(),
                media.getEndTimeMs(),
                media.getDurationMs()
        );
    }

    @Transactional
    public void completeJob(
            Long jobId,
            String originalFilename,
            String storageKey,
            String url,
            Long sizeBytes,
            Integer durationMs
    ) {
        AudioProcessingJob job = audioProcessingJobRepository.findById(jobId).orElseThrow();
        QuestionMedia media = job.getQuestionMedia();
        User uploader = media.getQuestion().getMap().getCreator();

        Asset asset = Asset.createAudio(
                uploader,
                originalFilename,
                storageKey,
                url,
                "audio/mpeg",
                sizeBytes,
                durationMs
        );
        asset.attach();
        assetRepository.save(asset);

        media.completeProcessing(asset, durationMs);
        job.succeed();
        publishMapIfAllMediaReady(media);
        long duration = job.getStartedAt() == null
                ? 0
                : Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis();
        log.info(
                "event=audio_job_succeeded jobId={} mapId={} questionId={} mediaId={} attempt={} queueWaitMs={} durationMs={} sizeBytes={}",
                jobId,
                media.getQuestion().getMap().getId(),
                media.getQuestion().getId(),
                media.getId(),
                job.getAttemptCount(),
                job.getCreatedAt() == null || job.getStartedAt() == null
                        ? 0
                        : Duration.between(job.getCreatedAt(), job.getStartedAt()).toMillis(),
                duration,
                sizeBytes
        );
    }

    @Transactional
    public AudioProcessingJobStatus handleJobFailure(
            Long jobId,
            AudioProcessingFailureCode failureCode,
            String failureMessage,
            int maxRetryAttempts
    ) {
        AudioProcessingJob job = audioProcessingJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("event=audio_job_failure_state_missing jobId={} failureCode={}", jobId, failureCode);
            return null;
        }
        retryOrFail(job, failureCode, failureMessage, maxRetryAttempts);
        return job.getStatus();
    }

    private void retryOrFail(AudioProcessingJob job, int maxRetryAttempts) {
        retryOrFail(
                job,
                AudioProcessingFailureCode.PROCESSING_TIMEOUT,
                "audio_processing_retry_exhausted",
                maxRetryAttempts
        );
    }

    private void retryOrFail(
            AudioProcessingJob job,
            AudioProcessingFailureCode failureCode,
            String failureMessage,
            int maxRetryAttempts
    ) {
        if (failureCode.isAutomaticRetryAllowed() && job.canAutomaticallyRetry(maxRetryAttempts)) {
            job.scheduleRetry(failureCode, failureMessage);
            return;
        }

        job.fail(failureCode, failureMessage);
        job.getQuestionMedia().getQuestion().getMap().failProcessing();
    }

    private void publishMapIfAllMediaReady(QuestionMedia media) {
        Long mapId = media.getQuestion().getMap().getId();
        boolean hasUnreadyMedia = questionMediaRepository.existsByQuestionMapIdAndProcessingStatusNot(
                mapId,
                QuestionMediaProcessingStatus.READY
        );

        if (!hasUnreadyMedia) {
            media.getQuestion().getMap().publishIfProcessing();
        }
    }
}
