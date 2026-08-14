package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaProcessingStatus;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import com.dogdog.nomat.domain.map.repository.QuestionMediaRepository;
import com.dogdog.nomat.domain.user.entity.User;
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
    public List<Long> getPendingJobIds(int batchSize) {
        return audioProcessingJobRepository
                .findByStatusOrderByCreatedAtAsc(AudioProcessingJobStatus.PENDING, PageRequest.of(0, batchSize))
                .stream()
                .map(AudioProcessingJob::getId)
                .toList();
    }

    @Transactional
    public AudioProcessingTask startJob(Long jobId) {
        AudioProcessingJob job = audioProcessingJobRepository.findById(jobId)
                .filter(foundJob -> foundJob.getStatus() == AudioProcessingJobStatus.PENDING)
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
    public void failJob(Long jobId, String failureMessage) {
        audioProcessingJobRepository.findById(jobId)
                .ifPresent(job -> job.fail(failureMessage));
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
