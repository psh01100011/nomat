package com.dogdog.nomat.domain.comment.entity;

import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "map_comments",
        indexes = {
                @Index(name = "idx_map_comments_map_created_at", columnList = "map_id, created_at"),
                @Index(name = "idx_map_comments_writer_id", columnList = "writer_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", nullable = false)
    private QuizMap map;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "writer_id", nullable = false)
    private User writer;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private MapCommentStatus status = MapCommentStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
