package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.domain.Specification;

public interface AudioProcessingJobRepository extends
        JpaRepository<AudioProcessingJob, Long>,
        JpaSpecificationExecutor<AudioProcessingJob> {

    List<AudioProcessingJob> findByStatusInOrderByCreatedAtAsc(
            Collection<AudioProcessingJobStatus> statuses,
            Pageable pageable
    );

    List<AudioProcessingJob> findByStatusAndStartedAtBeforeOrderByStartedAtAsc(
            AudioProcessingJobStatus status,
            LocalDateTime startedAt,
            Pageable pageable
    );

    Optional<AudioProcessingJob> findByQuestionMediaId(Long questionMediaId);

    long countByStatus(AudioProcessingJobStatus status);

    @Query("""
            SELECT MIN(job.createdAt)
            FROM AudioProcessingJob job
            WHERE job.status IN :statuses
            """)
    LocalDateTime findOldestCreatedAtByStatusIn(
            @Param("statuses") Collection<AudioProcessingJobStatus> statuses
    );

    @Query("""
            SELECT MIN(job.startedAt)
            FROM AudioProcessingJob job
            WHERE job.status = :status
            """)
    LocalDateTime findOldestStartedAtByStatus(@Param("status") AudioProcessingJobStatus status);

    @Override
    @EntityGraph(attributePaths = {"questionMedia", "questionMedia.question", "questionMedia.question.map"})
    Page<AudioProcessingJob> findAll(Specification<AudioProcessingJob> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"questionMedia", "questionMedia.question", "questionMedia.question.map"})
    @Query("SELECT job FROM AudioProcessingJob job WHERE job.id = :jobId")
    Optional<AudioProcessingJob> findByIdForUpdate(@Param("jobId") Long jobId);

    @EntityGraph(attributePaths = {"questionMedia", "questionMedia.question"})
    List<AudioProcessingJob> findByQuestionMediaQuestionMapIdAndStatusOrderByIdAsc(
            Long mapId,
            AudioProcessingJobStatus status
    );

    void deleteByQuestionMediaId(Long questionMediaId);

    void deleteByQuestionMediaQuestionId(Long questionId);

    void deleteByQuestionMediaQuestionMapId(Long mapId);
}
