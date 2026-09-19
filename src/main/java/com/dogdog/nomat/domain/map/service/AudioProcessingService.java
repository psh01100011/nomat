package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureType;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class AudioProcessingService {

    private final AudioProcessingJobService audioProcessingJobService;
    private final AudioExtractionCommandRunner audioExtractionCommandRunner;
    private final S3Client s3Client;
    private final AssetS3Properties s3Properties;
    private final AudioProcessingProperties processingProperties;

    public int processAvailableJobs() {
        audioProcessingJobService.recoverStaleProcessingJobs(
                processingProperties.getBatchSize(),
                processingProperties.getProcessingTimeoutMinutes(),
                processingProperties.getMaxRetryAttempts()
        );
        List<Long> jobIds = audioProcessingJobService.getProcessableJobIds(processingProperties.getBatchSize());
        int processedCount = 0;
        for (Long jobId : jobIds) {
            processJob(jobId);
            processedCount++;
        }

        return processedCount;
    }

    public void processJob(Long jobId) {
        AudioProcessingTask task = audioProcessingJobService.startJob(jobId);
        if (task == null) {
            return;
        }
        long processingStartedAt = System.nanoTime();

        Path workingDirectory = null;
        String storageKey = null;
        try {
            validateS3Configuration();

            workingDirectory = Files.createTempDirectory("nomat-audio-");
            Path outputFile = workingDirectory.resolve("question-media-%d.mp3".formatted(task.questionMediaId()));
            audioExtractionCommandRunner.extractYoutubeSegment(new YoutubeAudioExtractionCommand(
                    task.sourceUrl(),
                    task.startTimeMs(),
                    task.durationMs(),
                    outputFile
            ));

            storageKey = createAudioStorageKey();
            String url = createPublicUrl(storageKey);
            uploadToS3(outputFile, storageKey);

            try {
                audioProcessingJobService.completeJob(
                        task.jobId(),
                        outputFile.getFileName().toString(),
                        storageKey,
                        url,
                        Files.size(outputFile),
                        task.durationMs()
                );
            } catch (RuntimeException exception) {
                deleteFromS3Quietly(storageKey);
                throw exception;
            }
        } catch (AudioProcessingException exception) {
            AudioProcessingJobStatus status = audioProcessingJobService.handleJobFailure(
                    task.jobId(),
                    exception.getFailureCode(),
                    exception.getMessage(),
                    processingProperties.getMaxRetryAttempts()
            );
            logProcessingFailure(task, exception.getFailureCode(), status, exception, processingStartedAt);
        } catch (Exception exception) {
            AudioProcessingJobStatus status = audioProcessingJobService.handleJobFailure(
                    task.jobId(),
                    AudioProcessingFailureCode.UNKNOWN,
                    exception.getMessage(),
                    processingProperties.getMaxRetryAttempts()
            );
            logProcessingFailure(task, AudioProcessingFailureCode.UNKNOWN, status, exception, processingStartedAt);
        } finally {
            deleteWorkingDirectoryQuietly(workingDirectory);
        }
    }

    private void logProcessingFailure(
            AudioProcessingTask task,
            AudioProcessingFailureCode failureCode,
            AudioProcessingJobStatus status,
            Exception exception,
            long processingStartedAt
    ) {
        long durationMs = (System.nanoTime() - processingStartedAt) / 1_000_000;
        String message = "event=audio_job_attempt_failed jobId={} mapId={} questionId={} mediaId={} attempt={} queueWaitMs={} durationMs={} status={} failureCode={} errorType={}";
        if (status == AudioProcessingJobStatus.RETRYING) {
            log.warn(
                    message,
                    task.jobId(), task.mapId(), task.questionId(), task.questionMediaId(),
                    task.attemptCount(), task.queueWaitMs(), durationMs, status, failureCode,
                    exception.getClass().getSimpleName()
            );
        } else if (failureCode.getFailureType() == AudioProcessingFailureType.SOURCE) {
            log.info(
                    message,
                    task.jobId(), task.mapId(), task.questionId(), task.questionMediaId(),
                    task.attemptCount(), task.queueWaitMs(), durationMs, status, failureCode,
                    exception.getClass().getSimpleName()
            );
        } else {
            log.error(
                    message,
                    task.jobId(), task.mapId(), task.questionId(), task.questionMediaId(),
                    task.attemptCount(), task.queueWaitMs(), durationMs, status, failureCode,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void validateS3Configuration() throws AudioProcessingException {
        if (!StringUtils.hasText(s3Properties.getBucket())) {
            throw new AudioProcessingException(
                    AudioProcessingFailureCode.INTERNAL_ERROR,
                    "S3 bucket must be configured."
            );
        }
    }

    private String createAudioStorageKey() {
        LocalDate today = LocalDate.now();
        String prefix = s3Properties.normalizedAudioPrefix();
        String filename = UUID.randomUUID() + ".mp3";

        if (!StringUtils.hasText(prefix)) {
            return "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), filename);
        }

        return "%s/%d/%02d/%s".formatted(prefix, today.getYear(), today.getMonthValue(), filename);
    }

    private String createPublicUrl(String storageKey) {
        return s3Properties.resolvedPublicBaseUrl() + "/" + storageKey;
    }

    private void uploadToS3(Path outputFile, String storageKey) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(storageKey)
                .contentType("audio/mpeg")
                .contentLength(Files.size(outputFile))
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromFile(outputFile));
        } catch (SdkException exception) {
            throw new AudioProcessingException(
                    AudioProcessingFailureCode.STORAGE_ERROR,
                    "Failed to upload processed audio to S3.",
                    exception
            );
        }
    }

    private void deleteFromS3Quietly(String storageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(storageKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            log.warn("event=orphaned_audio_asset_cleanup_failed storageKey={}",
                    storageKey,
                    exception
            );
        }
    }

    private void deleteWorkingDirectoryQuietly(Path workingDirectory) {
        if (workingDirectory == null || !Files.exists(workingDirectory)) {
            return;
        }

        try (var paths = Files.walk(workingDirectory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            log.warn("event=audio_temp_file_cleanup_failed", exception);
                        }
                    });
        } catch (IOException exception) {
            log.warn("event=audio_temp_directory_cleanup_failed", exception);
        }
    }
}
