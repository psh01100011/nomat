package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizMapRepository extends JpaRepository<QuizMap, Long> {

    @EntityGraph(attributePaths = {"creator", "category", "thumbnailAsset"})
    Optional<QuizMap> findByIdAndStatusNot(Long id, MapStatus status);

    @EntityGraph(attributePaths = {"creator", "creator.profileImageAsset", "category", "thumbnailAsset"})
    Optional<QuizMap> findByIdAndStatusAndVisibility(Long id, MapStatus status, MapVisibility visibility);

    @EntityGraph(attributePaths = {"creator", "category", "thumbnailAsset"})
    @Query("""
            SELECT map
            FROM QuizMap map
            WHERE map.status IN :statuses
              AND (:visibility IS NULL OR map.visibility = :visibility)
              AND (:keyword IS NULL
                    OR LOWER(map.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(map.creator.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:categoryId IS NULL OR map.category.id = :categoryId)
              AND (:questionType IS NULL OR map.questionType = :questionType)
              AND (:creatorId IS NULL OR map.creator.id = :creatorId)
            """)
    Page<QuizMap> searchMaps(
            @Param("statuses") Collection<MapStatus> statuses,
            @Param("visibility") MapVisibility visibility,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("questionType") QuestionType questionType,
            @Param("creatorId") Long creatorId,
            Pageable pageable
    );
}
