package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.map.dto.AdminAudioProcessingJobListResponse;
import com.dogdog.nomat.domain.map.dto.AdminAudioProcessingJobResponse;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureType;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import com.dogdog.nomat.domain.map.entity.QuestionMediaProcessingStatus;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import com.dogdog.nomat.domain.map.repository.QuestionMediaRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAudioProcessingService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AudioProcessingJobRepository audioProcessingJobRepository;
    private final QuestionMediaRepository questionMediaRepository;
    private final AudioProcessingProperties processingProperties;

    @Transactional(readOnly = true)
    public AdminAudioProcessingJobListResponse getJobs(
            AudioProcessingJobStatus status,
            AudioProcessingFailureType failureType,
            Long mapId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            int page,
            int size
    ) {
        validateQuery(createdFrom, createdTo, page, size);
        Specification<AudioProcessingJob> specification = jobSpecification(
                status,
                failureType,
                mapId,
                createdFrom,
                createdTo
        );
        Page<AudioProcessingJob> jobs = audioProcessingJobRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return AdminAudioProcessingJobListResponse.from(jobs);
    }

    @Transactional
    public AdminAudioProcessingJobResponse retryFailedJob(Long jobId) {
        AudioProcessingJob job = getJobForUpdate(jobId);
        if (!job.canRetryManually()) {
            throw new BusinessException(HttpStatus.CONFLICT, "admin_audio_job_retry_not_allowed");
        }

        LocalDateTime requestedAt = LocalDateTime.now();
        job.retryManually(requestedAt);
        Long mapId = job.getQuestionMedia().getQuestion().getMap().getId();
        boolean hasOtherFailure = questionMediaRepository
                .existsByQuestionMapIdAndProcessingStatusAndIdNot(
                        mapId,
                        QuestionMediaProcessingStatus.FAILED,
                        job.getQuestionMedia().getId()
                );
        if (!hasOtherFailure) {
            job.getQuestionMedia().getQuestion().getMap().resumeProcessing();
        }

        log.warn(
                "event=admin_audio_job_retry_requested jobId={} mapId={} questionId={}",
                jobId,
                mapId,
                job.getQuestionMedia().getQuestion().getId()
        );
        return AdminAudioProcessingJobResponse.from(job);
    }

    @Transactional
    public AdminAudioProcessingJobResponse recoverStaleJob(Long jobId) {
        AudioProcessingJob job = getJobForUpdate(jobId);
        LocalDateTime staleBefore = LocalDateTime.now()
                .minusMinutes(processingProperties.getProcessingTimeoutMinutes());
        if (job.getStatus() != AudioProcessingJobStatus.PROCESSING
                || job.getStartedAt() == null
                || job.getStartedAt().isAfter(staleBefore)) {
            throw new BusinessException(HttpStatus.CONFLICT, "admin_audio_job_not_stale");
        }

        if (job.canAutomaticallyRetry(processingProperties.getMaxRetryAttempts())) {
            job.scheduleRetry(
                    AudioProcessingFailureCode.PROCESSING_TIMEOUT,
                    "audio_processing_recovered_by_operator"
            );
        } else {
            job.fail(
                    AudioProcessingFailureCode.PROCESSING_TIMEOUT,
                    "audio_processing_retry_exhausted"
            );
            job.getQuestionMedia().getQuestion().getMap().failProcessing();
        }

        log.warn(
                "event=admin_audio_job_stale_recovered jobId={} mapId={} status={} attempt={}",
                jobId,
                job.getQuestionMedia().getQuestion().getMap().getId(),
                job.getStatus(),
                job.getAttemptCount()
        );
        return AdminAudioProcessingJobResponse.from(job);
    }

    private AudioProcessingJob getJobForUpdate(Long jobId) {
        return audioProcessingJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "admin_audio_job_not_found"));
    }

    private void validateQuery(
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }
    }

    private Specification<AudioProcessingJob> jobSpecification(
            AudioProcessingJobStatus status,
            AudioProcessingFailureType failureType,
            Long mapId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (failureType != null) {
                List<AudioProcessingFailureCode> failureCodes = Arrays.stream(AudioProcessingFailureCode.values())
                        .filter(code -> code.getFailureType() == failureType)
                        .toList();
                predicates.add(root.get("failureCode").in(failureCodes));
            }
            if (mapId != null) {
                predicates.add(criteriaBuilder.equal(
                        root.join("questionMedia", JoinType.INNER)
                                .join("question", JoinType.INNER)
                                .join("map", JoinType.INNER)
                                .get("id"),
                        mapId
                ));
            }
            if (createdFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
