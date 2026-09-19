package com.dogdog.nomat.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.entity.AssetType;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaProcessingStatus;
import com.dogdog.nomat.domain.map.entity.QuestionMediaSourceType;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import com.dogdog.nomat.domain.map.repository.QuestionMediaRepository;
import com.dogdog.nomat.domain.map.monitoring.AudioProcessingMetrics;
import com.dogdog.nomat.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AudioProcessingJobServiceTest {

    @Mock
    private AudioProcessingJobRepository audioProcessingJobRepository;

    @Mock
    private QuestionMediaRepository questionMediaRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AudioProcessingMetrics processingMetrics;

    @InjectMocks
    private AudioProcessingJobService audioProcessingJobService;

    @Test
    void startJobMarksJobAndMediaProcessing() {
        AudioProcessingJob job = audioProcessingJob(1L);
        given(audioProcessingJobRepository.findByIdForUpdate(1L)).willReturn(Optional.of(job));

        AudioProcessingTask task = audioProcessingJobService.startJob(1L);

        assertThat(task.jobId()).isEqualTo(1L);
        assertThat(task.questionMediaId()).isEqualTo(10L);
        assertThat(task.sourceUrl()).isEqualTo("https://youtube.com/watch?v=---");
        assertThat(task.startTimeMs()).isEqualTo(60000);
        assertThat(task.endTimeMs()).isEqualTo(102000);
        assertThat(task.durationMs()).isEqualTo(42000);
        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.PROCESSING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.PROCESSING);
    }

    @Test
    void completeJobCreatesAttachedAudioAssetAndMarksMediaReady() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        given(audioProcessingJobRepository.findById(1L)).willReturn(Optional.of(job));
        given(assetRepository.save(any(Asset.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(questionMediaRepository.existsByQuestionMapIdAndProcessingStatusNot(100L, QuestionMediaProcessingStatus.READY))
                .willReturn(false);

        audioProcessingJobService.completeJob(
                1L,
                "question-media-10.mp3",
                "uploads/audios/2026/08/audio.mp3",
                "https://cdn.nomat.com/uploads/audios/2026/08/audio.mp3",
                1234L,
                42000
        );

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(assetCaptor.capture());
        Asset audioAsset = assetCaptor.getValue();
        assertThat(audioAsset.getUploader()).isEqualTo(job.getQuestionMedia().getQuestion().getMap().getCreator());
        assertThat(audioAsset.getAssetType()).isEqualTo(AssetType.AUDIO);
        assertThat(audioAsset.getStatus()).isEqualTo(AssetStatus.ATTACHED);
        assertThat(audioAsset.getDurationMs()).isEqualTo(42000);

        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.SUCCEEDED);
        assertThat(job.getQuestionMedia().getAsset()).isEqualTo(audioAsset);
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.READY);
        assertThat(job.getQuestionMedia().getFailureMessage()).isNull();
        assertThat(job.getQuestionMedia().getQuestion().getMap().getStatus()).isEqualTo(MapStatus.PUBLISHED);
        assertThat(job.getQuestionMedia().getQuestion().getMap().getPublishedAt()).isNotNull();
    }

    @Test
    void completeJobKeepsMapProcessingWhenAnotherMediaIsNotReady() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        given(audioProcessingJobRepository.findById(1L)).willReturn(Optional.of(job));
        given(assetRepository.save(any(Asset.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(questionMediaRepository.existsByQuestionMapIdAndProcessingStatusNot(100L, QuestionMediaProcessingStatus.READY))
                .willReturn(true);

        audioProcessingJobService.completeJob(
                1L,
                "question-media-10.mp3",
                "uploads/audios/2026/08/audio.mp3",
                "https://cdn.nomat.com/uploads/audios/2026/08/audio.mp3",
                1234L,
                42000
        );

        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.SUCCEEDED);
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.READY);
        assertThat(job.getQuestionMedia().getQuestion().getMap().getStatus()).isEqualTo(MapStatus.PROCESSING);
        assertThat(job.getQuestionMedia().getQuestion().getMap().getPublishedAt()).isNull();
    }

    @Test
    void handleJobFailureSchedulesRetryWhenAttemptsRemain() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        given(audioProcessingJobRepository.findById(1L)).willReturn(Optional.of(job));

        audioProcessingJobService.handleJobFailure(
                1L,
                AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR,
                "extract failed",
                3
        );

        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.RETRYING);
        assertThat(job.getFailureCode()).isEqualTo(AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR);
        assertThat(job.getFailureMessage()).isEqualTo("extract failed");
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.RETRYING);
        assertThat(job.getQuestionMedia().getFailureMessage()).isNull();
        assertThat(job.canRetryManually()).isFalse();
        assertThat(job.getQuestionMedia().canRetry()).isFalse();
    }

    @Test
    void handleJobFailureMarksFinalFailureWhenRetryAttemptsAreExhausted() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        job.scheduleRetry(AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR, "first failure");
        job.start();
        given(audioProcessingJobRepository.findById(1L)).willReturn(Optional.of(job));

        audioProcessingJobService.handleJobFailure(
                1L,
                AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR,
                "extract failed",
                2
        );

        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR);
        assertThat(job.getFailureMessage()).isEqualTo("extract failed");
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.FAILED);
        assertThat(job.getQuestionMedia().getFailureCode()).isEqualTo(AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR);
        assertThat(job.getQuestionMedia().getFailureMessage())
                .isEqualTo(AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR.getUserMessage());
        assertThat(job.getQuestionMedia().getQuestion().getMap().getStatus())
                .isEqualTo(MapStatus.PROCESSING_FAILED);
        assertThat(job.canRetryManually()).isTrue();
        assertThat(job.getQuestionMedia().canRetry()).isTrue();
    }

    @Test
    void handleJobFailureImmediatelyFailsForSourceProblem() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        given(audioProcessingJobRepository.findById(1L)).willReturn(Optional.of(job));

        audioProcessingJobService.handleJobFailure(
                1L,
                AudioProcessingFailureCode.SOURCE_UNAVAILABLE,
                "Video unavailable",
                3
        );

        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.FAILED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getFailureCode()).isEqualTo(AudioProcessingFailureCode.SOURCE_UNAVAILABLE);
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.FAILED);
        assertThat(job.getQuestionMedia().getFailureCode()).isEqualTo(AudioProcessingFailureCode.SOURCE_UNAVAILABLE);
        assertThat(job.getQuestionMedia().getFailureMessage())
                .isEqualTo(AudioProcessingFailureCode.SOURCE_UNAVAILABLE.getUserMessage());
        assertThat(job.getQuestionMedia().getQuestion().getMap().getStatus())
                .isEqualTo(MapStatus.PROCESSING_FAILED);
        assertThat(job.canRetryManually()).isFalse();
        assertThat(job.getQuestionMedia().canRetry()).isFalse();
    }

    @Test
    void unknownFailureRetriesAutomaticallyThenAllowsManualRetry() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        given(audioProcessingJobRepository.findById(1L)).willReturn(Optional.of(job));

        audioProcessingJobService.handleJobFailure(
                1L,
                AudioProcessingFailureCode.UNKNOWN,
                "unexpected exception detail",
                2
        );

        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.RETRYING);
        assertThat(job.getFailureCode()).isEqualTo(AudioProcessingFailureCode.UNKNOWN);
        assertThat(job.canRetryManually()).isFalse();
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.RETRYING);

        job.start();
        audioProcessingJobService.handleJobFailure(
                1L,
                AudioProcessingFailureCode.UNKNOWN,
                "unexpected exception detail",
                2
        );

        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(AudioProcessingFailureCode.UNKNOWN);
        assertThat(job.canRetryManually()).isTrue();
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.FAILED);
        assertThat(job.getQuestionMedia().getFailureCode()).isEqualTo(AudioProcessingFailureCode.UNKNOWN);
        assertThat(job.getQuestionMedia().getFailureMessage())
                .isEqualTo(AudioProcessingFailureCode.UNKNOWN.getUserMessage());
        assertThat(job.getQuestionMedia().getFailureMessage()).doesNotContain("unexpected exception detail");
        assertThat(job.getQuestionMedia().canRetry()).isTrue();
    }

    @Test
    void recoverStaleProcessingJobsRetriesJobsAndMedia() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        ReflectionTestUtils.setField(job, "startedAt", LocalDateTime.now().minusMinutes(20));
        given(audioProcessingJobRepository.findByStatusAndStartedAtBeforeOrderByStartedAtAsc(
                any(),
                any(),
                any()
        )).willReturn(List.of(job));

        int recoveredCount = audioProcessingJobService.recoverStaleProcessingJobs(5, 10, 3);

        assertThat(recoveredCount).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.RETRYING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.RETRYING);
    }

    @Test
    void recoverStaleProcessingJobsFailsJobWhenRetryAttemptsAreExhausted() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        job.scheduleRetry(AudioProcessingFailureCode.PROCESSING_TIMEOUT, "first failure");
        job.start();
        ReflectionTestUtils.setField(job, "startedAt", LocalDateTime.now().minusMinutes(20));
        given(audioProcessingJobRepository.findByStatusAndStartedAtBeforeOrderByStartedAtAsc(
                any(),
                any(),
                any()
        )).willReturn(List.of(job));

        int recoveredCount = audioProcessingJobService.recoverStaleProcessingJobs(5, 10, 2);

        assertThat(recoveredCount).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.FAILED);
        assertThat(job.getFailureMessage()).isEqualTo("audio_processing_retry_exhausted");
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.FAILED);
    }

    @Test
    void startJobStartsRetryingJob() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        job.scheduleRetry(AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR, "extract failed");
        given(audioProcessingJobRepository.findByIdForUpdate(1L)).willReturn(Optional.of(job));

        AudioProcessingTask task = audioProcessingJobService.startJob(1L);

        assertThat(task).isNotNull();
        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.PROCESSING);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getFailureMessage()).isNull();
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.PROCESSING);
        assertThat(job.getQuestionMedia().getFailureMessage()).isNull();
    }

    @Test
    void startJobSkipsJobAlreadyClaimedByAnotherWorker() {
        AudioProcessingJob job = audioProcessingJob(1L);
        job.start();
        given(audioProcessingJobRepository.findByIdForUpdate(1L)).willReturn(Optional.of(job));

        AudioProcessingTask task = audioProcessingJobService.startJob(1L);

        assertThat(task).isNull();
        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.PROCESSING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
    }

    private AudioProcessingJob audioProcessingJob(Long id) {
        User creator = User.create("testuser", "encoded-password", "tester");
        ReflectionTestUtils.setField(creator, "id", 1L);
        Category category = Category.create("음악");
        QuizMap map = QuizMap.create(
                creator,
                category,
                null,
                QuestionType.AUDIO,
                "오디오 퀴즈",
                "설명",
                MapVisibility.PUBLIC,
                1,
                MapStatus.PROCESSING
        );
        ReflectionTestUtils.setField(map, "id", 100L);
        Question question = Question.create(map, 1, "이 노래는?");
        QuestionMedia media = QuestionMedia.create(
                question,
                null,
                QuestionMediaSourceType.YOUTUBE,
                "https://youtube.com/watch?v=---",
                60000,
                102000,
                42000
        );
        ReflectionTestUtils.setField(media, "id", 10L);

        AudioProcessingJob job = AudioProcessingJob.create(media);
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }
}
