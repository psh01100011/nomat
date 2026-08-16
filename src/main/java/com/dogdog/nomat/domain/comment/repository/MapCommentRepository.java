package com.dogdog.nomat.domain.comment.repository;

import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.comment.entity.MapCommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapCommentRepository extends JpaRepository<MapComment, Long> {

    @EntityGraph(attributePaths = {"writer", "writer.profileImageAsset"})
    Page<MapComment> findByMapIdAndStatus(Long mapId, MapCommentStatus status, Pageable pageable);
}
