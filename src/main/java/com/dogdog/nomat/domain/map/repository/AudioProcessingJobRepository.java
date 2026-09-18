package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AudioProcessingJobRepository extends JpaRepository<AudioProcessingJob, Long> {

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"questionMedia"})
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
