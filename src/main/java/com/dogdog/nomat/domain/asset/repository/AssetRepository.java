package com.dogdog.nomat.domain.asset.repository;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.QuestionStatus;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Asset> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            AssetStatus status,
            LocalDateTime createdAt,
            Pageable pageable
    );

    @Query("""
            SELECT asset.id
            FROM Asset asset
            WHERE asset.status = :assetStatus
              AND asset.id > :afterId
            ORDER BY asset.id ASC
            """)
    List<Long> findAssetIdsAfter(
            @Param("assetStatus") AssetStatus assetStatus,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT asset
            FROM Asset asset
            WHERE asset.id IN :assetIds
              AND asset.status = :assetStatus
              AND NOT EXISTS (
                  SELECT account.id
                  FROM User account
                  WHERE account.profileImageAsset = asset
                    AND account.status <> :deletedUserStatus
              )
              AND NOT EXISTS (
                  SELECT quizMap.id
                  FROM QuizMap quizMap
                  WHERE quizMap.thumbnailAsset = asset
                    AND quizMap.status <> :deletedMapStatus
              )
              AND NOT EXISTS (
                  SELECT media.id
                  FROM QuestionMedia media
                  WHERE media.asset = asset
                    AND media.question.status <> :deletedQuestionStatus
                    AND media.question.map.status <> :deletedMapStatus
              )
            """)
    List<Asset> findUnreferencedAssetsByIdIn(
            @Param("assetIds") List<Long> assetIds,
            @Param("assetStatus") AssetStatus assetStatus,
            @Param("deletedUserStatus") UserStatus deletedUserStatus,
            @Param("deletedMapStatus") MapStatus deletedMapStatus,
            @Param("deletedQuestionStatus") QuestionStatus deletedQuestionStatus
    );

    @Query("""
            SELECT asset.id
            FROM Asset asset
            WHERE asset.status = :assetStatus
              AND asset.orphanedAt < :orphanedBefore
            ORDER BY asset.orphanedAt ASC, asset.id ASC
            """)
    List<Long> findOrphanedAssetIdsBefore(
            @Param("assetStatus") AssetStatus assetStatus,
            @Param("orphanedBefore") LocalDateTime orphanedBefore,
            Pageable pageable
    );

    @Query("""
            SELECT CASE WHEN COUNT(asset) > 0 THEN true ELSE false END
            FROM Asset asset
            WHERE asset.id = :assetId
              AND (
                  EXISTS (
                      SELECT account.id
                      FROM User account
                      WHERE account.profileImageAsset = asset
                        AND account.status <> :deletedUserStatus
                  )
                  OR EXISTS (
                      SELECT quizMap.id
                      FROM QuizMap quizMap
                      WHERE quizMap.thumbnailAsset = asset
                        AND quizMap.status <> :deletedMapStatus
                  )
                  OR EXISTS (
                      SELECT media.id
                      FROM QuestionMedia media
                      WHERE media.asset = asset
                        AND media.question.status <> :deletedQuestionStatus
                        AND media.question.map.status <> :deletedMapStatus
                  )
              )
            """)
    boolean existsActiveReference(
            @Param("assetId") Long assetId,
            @Param("deletedUserStatus") UserStatus deletedUserStatus,
            @Param("deletedMapStatus") MapStatus deletedMapStatus,
            @Param("deletedQuestionStatus") QuestionStatus deletedQuestionStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "uploader")
    @Query("SELECT asset FROM Asset asset WHERE asset.id = :assetId")
    Optional<Asset> findByIdForUpdate(@Param("assetId") Long assetId);
}
