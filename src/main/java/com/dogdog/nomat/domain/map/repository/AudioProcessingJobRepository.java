package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioProcessingJobRepository extends JpaRepository<AudioProcessingJob, Long> {

    List<AudioProcessingJob> findByStatusOrderByCreatedAtAsc(AudioProcessingJobStatus status, Pageable pageable);
}
