package com.dogdog.nomat.domain.map.entity;

import com.dogdog.nomat.domain.asset.entity.Asset;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "maps",
        indexes = {
                @Index(name = "idx_maps_creator_id", columnList = "creator_id"),
                @Index(name = "idx_maps_category_id", columnList = "category_id"),
                @Index(name = "idx_maps_question_type", columnList = "question_type"),
                @Index(name = "idx_maps_status", columnList = "status"),
                @Index(name = "idx_maps_visibility", columnList = "visibility"),
                @Index(name = "idx_maps_created_at", columnList = "created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_asset_id")
    private Asset thumbnailAsset;

    @Column(name = "question_type", length = 20)
    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @Column(name = "title", length = 100)
    private String title;

    @Lob
    @Column(name = "description")
    private String description;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private MapStatus status = MapStatus.DRAFT;

    @Column(name = "visibility", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private MapVisibility visibility = MapVisibility.PUBLIC;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "play_count", nullable = false)
    private long playCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "favorite_count", nullable = false)
    private long favoriteCount;

    @Column(name = "comment_count", nullable = false)
    private long commentCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private QuizMap(
            User creator,
            Category category,
            Asset thumbnailAsset,
            QuestionType questionType,
            String title,
            String description,
            MapVisibility visibility,
            int questionCount,
            MapStatus status
    ) {
        this.creator = creator;
        this.category = category;
        this.thumbnailAsset = thumbnailAsset;
        this.questionType = questionType;
        this.title = title;
        this.description = description;
        this.status = status;
        this.visibility = visibility;
        this.questionCount = questionCount;
        if (status == MapStatus.PUBLISHED) {
            this.publishedAt = LocalDateTime.now();
        }
    }

    public static QuizMap create(
            User creator,
            Category category,
            Asset thumbnailAsset,
            QuestionType questionType,
            String title,
            String description,
            MapVisibility visibility,
            int questionCount,
            MapStatus status
    ) {
        return new QuizMap(
                creator,
                category,
                thumbnailAsset,
                questionType,
                title,
                description,
                visibility,
                questionCount,
                status
        );
    }

    public static QuizMap publish(
            User creator,
            Category category,
            Asset thumbnailAsset,
            QuestionType questionType,
            String title,
            String description,
            MapVisibility visibility,
            int questionCount
    ) {
        return create(
                creator,
                category,
                thumbnailAsset,
                questionType,
                title,
                description,
                visibility,
                questionCount,
                MapStatus.PUBLISHED
        );
    }

    public static QuizMap draft(
            User creator,
            Category category,
            Asset thumbnailAsset,
            QuestionType questionType,
            String title,
            String description,
            MapVisibility visibility,
            int questionCount
    ) {
        return create(
                creator,
                category,
                thumbnailAsset,
                questionType,
                title,
                description,
                visibility,
                questionCount,
                MapStatus.DRAFT
        );
    }

    public void publishIfProcessing() {
        if (status != MapStatus.PROCESSING) {
            return;
        }

        this.status = MapStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void modify(
            Category category,
            Asset thumbnailAsset,
            QuestionType questionType,
            String title,
            String description,
            MapVisibility visibility,
            int questionCount,
            MapStatus status
    ) {
        this.category = category;
        this.thumbnailAsset = thumbnailAsset;
        this.questionType = questionType;
        this.title = title;
        this.description = description;
        this.visibility = visibility;
        this.questionCount = questionCount;
        changeStatus(status);
        this.version++;
    }

    public void delete() {
        if (status == MapStatus.DELETED) {
            return;
        }

        this.status = MapStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (likeCount > 0) {
            this.likeCount--;
        }
    }

    public void increaseFavoriteCount() {
        this.favoriteCount++;
    }

    public void decreaseFavoriteCount() {
        if (favoriteCount > 0) {
            this.favoriteCount--;
        }
    }

    private void changeStatus(MapStatus status) {
        this.status = status;
        if (status == MapStatus.PUBLISHED && this.publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
