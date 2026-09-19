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
import com.dogdog.nomat.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AudioProcessingJobService {

    private final AudioProcessingJobRepository audioProcessingJobRepository;
    private final QuestionMediaRepository questionMediaRepository;
    private final AssetRepository assetRepository;

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
        jobs.forEach(job -> retryOrFail(job, maxRetryAttempts));
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
        return new AudioProcessingTask(
                job.getId(),
                media.getId(),
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
    }

    @Transactional
    public void handleJobFailure(
            Long jobId,
            AudioProcessingFailureCode failureCode,
            String failureMessage,
            int maxRetryAttempts
    ) {
        audioProcessingJobRepository.findById(jobId)
                .ifPresent(job -> retryOrFail(job, failureCode, failureMessage, maxRetryAttempts));
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
