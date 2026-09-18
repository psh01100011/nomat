package com.dogdog.nomat.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class AudioProcessingServiceTest {

    @Mock
    private AudioProcessingJobService audioProcessingJobService;

    @Mock
    private AudioExtractionCommandRunner audioExtractionCommandRunner;

    @Mock
    private S3Client s3Client;

    private AssetS3Properties s3Properties;
    private AudioProcessingProperties processingProperties;
    private AudioProcessingService audioProcessingService;

    @BeforeEach
    void setUp() {
        s3Properties = new AssetS3Properties();
        s3Properties.setBucket("nomat-assets");
        s3Properties.setRegion("ap-northeast-2");
        s3Properties.setPublicBaseUrl("https://cdn.nomat.com");
        s3Properties.setImagePrefix("uploads/images");
        s3Properties.setAudioPrefix("uploads/audios");
        s3Properties.setMaxImageSizeBytes(10 * 1024 * 1024);

        processingProperties = new AudioProcessingProperties();
        processingProperties.setBatchSize(5);
        processingProperties.setCommandTimeoutSeconds(300);

        audioProcessingService = new AudioProcessingService(
                audioProcessingJobService,
                audioExtractionCommandRunner,
                s3Client,
                s3Properties,
                processingProperties
        );
    }

    @Test
    void processAvailableJobsProcessesConfiguredBatch() throws Exception {
        given(audioProcessingJobService.getProcessableJobIds(5)).willReturn(List.of(1L, 2L));
        given(audioProcessingJobService.startJob(1L)).willReturn(audioProcessingTask(1L));
        given(audioProcessingJobService.startJob(2L)).willReturn(null);
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        org.mockito.BDDMockito.willAnswer(invocation -> {
                    YoutubeAudioExtractionCommand command = invocation.getArgument(0);
                    Files.writeString(command.outputFile(), "mp3");
                    return null;
                })
                .given(audioExtractionCommandRunner)
                .extractYoutubeSegment(any(YoutubeAudioExtractionCommand.class));

        int processedCount = audioProcessingService.processAvailableJobs();

        assertThat(processedCount).isEqualTo(2);
        verify(audioProcessingJobService).startJob(1L);
        verify(audioProcessingJobService).startJob(2L);
    }

    @Test
    void processJobExtractsUploadsAndCompletesAudioJob() throws Exception {
        AudioProcessingTask task = audioProcessingTask(1L);
        given(audioProcessingJobService.startJob(1L)).willReturn(task);
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        org.mockito.BDDMockito.willAnswer(invocation -> {
                    YoutubeAudioExtractionCommand command = invocation.getArgument(0);
                    Files.writeString(command.outputFile(), "mp3");
                    return null;
                })
                .given(audioExtractionCommandRunner)
                .extractYoutubeSegment(any(YoutubeAudioExtractionCommand.class));

        audioProcessingService.processJob(1L);

        ArgumentCaptor<YoutubeAudioExtractionCommand> commandCaptor =
                ArgumentCaptor.forClass(YoutubeAudioExtractionCommand.class);
        verify(audioExtractionCommandRunner).extractYoutubeSegment(commandCaptor.capture());

        YoutubeAudioExtractionCommand command = commandCaptor.getValue();
        assertThat(command.sourceUrl()).isEqualTo("https://youtube.com/watch?v=---");
        assertThat(command.startTimeMs()).isEqualTo(60000);
        assertThat(command.durationMs()).isEqualTo(42000);
        assertThat(command.outputFile().getFileName().toString()).isEqualTo("question-media-10.mp3");

        ArgumentCaptor<PutObjectRequest> putObjectCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putObjectCaptor.capture(), any(RequestBody.class));
        assertThat(putObjectCaptor.getValue().bucket()).isEqualTo("nomat-assets");
        assertThat(putObjectCaptor.getValue().key()).startsWith("uploads/audios/");
        assertThat(putObjectCaptor.getValue().key()).endsWith(".mp3");
        assertThat(putObjectCaptor.getValue().contentType()).isEqualTo("audio/mpeg");

        verify(audioProcessingJobService).completeJob(
                eq(1L),
                eq("question-media-10.mp3"),
                eq(putObjectCaptor.getValue().key()),
                eq("https://cdn.nomat.com/" + putObjectCaptor.getValue().key()),
                eq(3L),
                eq(42000)
        );
        verify(audioProcessingJobService, never()).handleJobFailure(
                eq(1L),
                any(AudioProcessingFailureCode.class),
                any(),
                any(Integer.class)
        );
    }

    @Test
    void processJobDelegatesFailureLifecycleWhenExtractionFails() throws Exception {
        AudioProcessingTask task = audioProcessingTask(1L);
        given(audioProcessingJobService.startJob(1L)).willReturn(task);
        org.mockito.BDDMockito.willThrow(new java.io.IOException("extract failed"))
                .given(audioExtractionCommandRunner)
                .extractYoutubeSegment(any(YoutubeAudioExtractionCommand.class));

        audioProcessingService.processJob(1L);

        verify(audioProcessingJobService).handleJobFailure(
                1L,
                AudioProcessingFailureCode.UNKNOWN,
                "extract failed",
                3
        );
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void processJobPassesClassifiedSourceFailureToLifecycle() throws Exception {
        AudioProcessingTask task = audioProcessingTask(1L);
        given(audioProcessingJobService.startJob(1L)).willReturn(task);
        org.mockito.BDDMockito.willThrow(new AudioProcessingException(
                        AudioProcessingFailureCode.SOURCE_UNAVAILABLE,
                        "Video unavailable"
                ))
                .given(audioExtractionCommandRunner)
                .extractYoutubeSegment(any(YoutubeAudioExtractionCommand.class));

        audioProcessingService.processJob(1L);

        verify(audioProcessingJobService).handleJobFailure(
                1L,
                AudioProcessingFailureCode.SOURCE_UNAVAILABLE,
                "Video unavailable",
                3
        );
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void processJobClassifiesS3UploadFailureAsStorageError() throws Exception {
        AudioProcessingTask task = audioProcessingTask(1L);
        given(audioProcessingJobService.startJob(1L)).willReturn(task);
        org.mockito.BDDMockito.willAnswer(invocation -> {
                    YoutubeAudioExtractionCommand command = invocation.getArgument(0);
                    Files.writeString(command.outputFile(), "mp3");
                    return null;
                })
                .given(audioExtractionCommandRunner)
                .extractYoutubeSegment(any(YoutubeAudioExtractionCommand.class));
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(SdkClientException.builder().message("S3 unavailable").build());

        audioProcessingService.processJob(1L);

        verify(audioProcessingJobService).handleJobFailure(
                1L,
                AudioProcessingFailureCode.STORAGE_ERROR,
                "Failed to upload processed audio to S3.",
                3
        );
    }

    private AudioProcessingTask audioProcessingTask(Long jobId) {
        return new AudioProcessingTask(
                jobId,
                10L,
                "https://youtube.com/watch?v=---",
                60000,
                102000,
                42000
        );
    }
}
