package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizMapRepository extends JpaRepository<QuizMap, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE QuizMap map
            SET map.version = map.version + 1
            WHERE map.id = :mapId
              AND map.version = :expectedVersion
            """)
    int claimVersion(@Param("mapId") Long mapId, @Param("expectedVersion") int expectedVersion);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE QuizMap map SET map.likeCount = map.likeCount + 1 WHERE map.id = :mapId")
    int incrementLikeCount(@Param("mapId") Long mapId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE QuizMap map
            SET map.likeCount = map.likeCount - 1
            WHERE map.id = :mapId AND map.likeCount > 0
            """)
    int decrementLikeCount(@Param("mapId") Long mapId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE QuizMap map SET map.favoriteCount = map.favoriteCount + 1 WHERE map.id = :mapId")
    int incrementFavoriteCount(@Param("mapId") Long mapId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE QuizMap map
            SET map.favoriteCount = map.favoriteCount - 1
            WHERE map.id = :mapId AND map.favoriteCount > 0
            """)
    int decrementFavoriteCount(@Param("mapId") Long mapId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE QuizMap map SET map.commentCount = map.commentCount + 1 WHERE map.id = :mapId")
    int incrementCommentCount(@Param("mapId") Long mapId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE QuizMap map
            SET map.commentCount = map.commentCount - 1
            WHERE map.id = :mapId AND map.commentCount > 0
            """)
    int decrementCommentCount(@Param("mapId") Long mapId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE QuizMap map
            SET map.playCount = map.playCount + :playerCount
            WHERE map.id = :mapId
            """)
    int addPlayCount(@Param("mapId") Long mapId, @Param("playerCount") int playerCount);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE QuizMap map
            SET map.likeCount = :likeCount,
                map.favoriteCount = :favoriteCount,
                map.commentCount = :commentCount,
                map.playCount = :playCount
            WHERE map.id = :mapId
            """)
    int replaceCounters(
            @Param("mapId") Long mapId,
            @Param("likeCount") long likeCount,
            @Param("favoriteCount") long favoriteCount,
            @Param("commentCount") long commentCount,
            @Param("playCount") long playCount
    );

    @Query("SELECT map.likeCount FROM QuizMap map WHERE map.id = :mapId")
    long findLikeCount(@Param("mapId") Long mapId);

    @Query("SELECT map.favoriteCount FROM QuizMap map WHERE map.id = :mapId")
    long findFavoriteCount(@Param("mapId") Long mapId);

    @Query("SELECT map.version FROM QuizMap map WHERE map.id = :mapId")
    int findVersion(@Param("mapId") Long mapId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT map FROM QuizMap map WHERE map.id = :mapId")
    Optional<QuizMap> findByIdForUpdate(@Param("mapId") Long mapId);

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
