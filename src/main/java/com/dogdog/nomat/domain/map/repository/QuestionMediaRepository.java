package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionMediaRepository extends JpaRepository<QuestionMedia, Long> {

    boolean existsByQuestionMapIdAndProcessingStatusNot(
            Long mapId,
            QuestionMediaProcessingStatus processingStatus
    );
}
