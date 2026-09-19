package com.dogdog.nomat.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.map.dto.AdminAudioProcessingJobResponse;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
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
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAudioProcessingServiceTest {

    @Mock
    private AudioProcessingJobRepository audioProcessingJobRepository;

    @Mock
    private QuestionMediaRepository questionMediaRepository;

    private AudioProcessingProperties processingProperties;
    private AdminAudioProcessingService service;

    @BeforeEach
    void setUp() {
        processingProperties = new AudioProcessingProperties();
        processingProperties.setProcessingTimeoutMinutes(10);
        processingProperties.setMaxRetryAttempts(3);
        service = new AdminAudioProcessingService(
                audioProcessingJobRepository,
                questionMediaRepository,
                processingProperties
        );
    }

    @Test
    void retriesOnlyFinalServerFailure() {
        AudioProcessingJob job = failedJob(AudioProcessingFailureCode.STORAGE_ERROR);
        given(audioProcessingJobRepository.findByIdForUpdate(1L)).willReturn(Optional.of(job));
        given(questionMediaRepository.existsByQuestionMapIdAndProcessingStatusAndIdNot(
                100L,
                QuestionMediaProcessingStatus.FAILED,
                10L
        )).willReturn(false);

        AdminAudioProcessingJobResponse response = service.retryFailedJob(1L);

        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.QUEUED);
        assertThat(job.getQuestionMedia().getQuestion().getMap().getStatus()).isEqualTo(MapStatus.PROCESSING);
    }

    @Test
    void rejectsSourceFailureRetry() {
        AudioProcessingJob job = failedJob(AudioProcessingFailureCode.SOURCE_UNAVAILABLE);
        given(audioProcessingJobRepository.findByIdForUpdate(1L)).willReturn(Optional.of(job));

        assertThatThrownBy(() -> service.retryFailedJob(1L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessageCode()).isEqualTo("admin_audio_job_retry_not_allowed"));
    }

    @Test
    void recoversOnlyProcessingJobPastStaleThreshold() {
        AudioProcessingJob job = processingJob();
        ReflectionTestUtils.setField(job, "startedAt", LocalDateTime.now().minusMinutes(20));
        given(audioProcessingJobRepository.findByIdForUpdate(1L)).willReturn(Optional.of(job));

        AdminAudioProcessingJobResponse response = service.recoverStaleJob(1L);

        assertThat(response.status()).isEqualTo("RETRYING");
        assertThat(job.getFailureCode()).isEqualTo(AudioProcessingFailureCode.PROCESSING_TIMEOUT);
        assertThat(job.getQuestionMedia().getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.RETRYING);
    }

    @Test
    void rejectsProcessingJobBeforeStaleThreshold() {
        AudioProcessingJob job = processingJob();
        ReflectionTestUtils.setField(job, "startedAt", LocalDateTime.now().minusMinutes(5));
        given(audioProcessingJobRepository.findByIdForUpdate(1L)).willReturn(Optional.of(job));

        assertThatThrownBy(() -> service.recoverStaleJob(1L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessageCode()).isEqualTo("admin_audio_job_not_stale"));
    }

    @Test
    void rejectsInvalidQueryRangeAndPageSize() {
        assertThatThrownBy(() -> service.getJobs(
                null,
                null,
                null,
                LocalDateTime.of(2026, 9, 2, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0),
                0,
                101
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getMessageCode()).isEqualTo("invalid_request"));
    }

    private AudioProcessingJob failedJob(AudioProcessingFailureCode failureCode) {
        AudioProcessingJob job = processingJob();
        job.fail(failureCode, "internal failure detail");
        job.getQuestionMedia().getQuestion().getMap().failProcessing();
        return job;
    }

    private AudioProcessingJob processingJob() {
        User creator = User.create("testuser", "encoded-password", "tester");
        ReflectionTestUtils.setField(creator, "id", 1L);
        QuizMap map = QuizMap.create(
                creator,
                Category.create("music"),
                null,
                QuestionType.AUDIO,
                "audio quiz",
                "description",
                MapVisibility.PUBLIC,
                1,
                MapStatus.PROCESSING
        );
        ReflectionTestUtils.setField(map, "id", 100L);
        Question question = Question.create(map, 1, "question");
        ReflectionTestUtils.setField(question, "id", 20L);
        QuestionMedia media = QuestionMedia.create(
                question,
                null,
                QuestionMediaSourceType.YOUTUBE,
                "https://youtube.com/watch?v=---",
                0,
                30000,
                30000
        );
        ReflectionTestUtils.setField(media, "id", 10L);
        AudioProcessingJob job = AudioProcessingJob.create(media);
        ReflectionTestUtils.setField(job, "id", 1L);
        ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.now().minusMinutes(30));
        ReflectionTestUtils.setField(job, "updatedAt", LocalDateTime.now().minusMinutes(20));
        job.start();
        return job;
    }
}
