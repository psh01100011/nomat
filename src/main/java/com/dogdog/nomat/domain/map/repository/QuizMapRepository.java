package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizMapRepository extends JpaRepository<QuizMap, Long> {

    @EntityGraph(attributePaths = {"creator", "category", "thumbnailAsset"})
    Optional<QuizMap> findByIdAndStatusNot(Long id, MapStatus status);
}
