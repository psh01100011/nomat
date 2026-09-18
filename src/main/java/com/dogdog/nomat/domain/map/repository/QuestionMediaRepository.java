package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaProcessingStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionMediaRepository extends JpaRepository<QuestionMedia, Long> {

    boolean existsByQuestionMapIdAndProcessingStatusNot(
            Long mapId,
            QuestionMediaProcessingStatus processingStatus
    );

    @EntityGraph(attributePaths = {"asset"})
    List<QuestionMedia> findByQuestionIdIn(Collection<Long> questionIds);

    @EntityGraph(attributePaths = {"question", "question.map"})
    List<QuestionMedia> findByQuestionMapIdIn(Collection<Long> mapIds);

    Optional<QuestionMedia> findByQuestionId(Long questionId);

    void deleteByQuestionId(Long questionId);

    void deleteByQuestionIdIn(Collection<Long> questionIds);
}
