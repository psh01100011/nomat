package com.dogdog.nomat.domain.comment.repository;

import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.comment.entity.MapCommentStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapCommentRepository extends JpaRepository<MapComment, Long> {

    @EntityGraph(attributePaths = {"writer", "writer.profileImageAsset"})
    Page<MapComment> findByMapIdAndStatus(Long mapId, MapCommentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"writer", "map"})
    Optional<MapComment> findByIdAndStatus(Long id, MapCommentStatus status);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE MapComment comment
            SET comment.status = :deletedStatus,
                comment.updatedAt = :deletedAt,
                comment.deletedAt = :deletedAt
            WHERE comment.id = :commentId AND comment.status = :activeStatus
            """)
    int deleteIfActive(
            @Param("commentId") Long commentId,
            @Param("activeStatus") MapCommentStatus activeStatus,
            @Param("deletedStatus") MapCommentStatus deletedStatus,
            @Param("deletedAt") java.time.LocalDateTime deletedAt
    );

    @Query("""
            SELECT COUNT(comment)
            FROM MapComment comment
            WHERE comment.map.id = :mapId AND comment.status = :status
            """)
    long countByMapIdAndStatus(@Param("mapId") Long mapId, @Param("status") MapCommentStatus status);
}
