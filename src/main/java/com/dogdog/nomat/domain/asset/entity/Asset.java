package com.dogdog.nomat.domain.asset.entity;

import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
@Table(name = "assets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "asset_type", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "storage_key", length = 500, nullable = false)
    private String storageKey;

    @Column(name = "url", length = 500, nullable = false)
    private String url;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private AssetStatus status = AssetStatus.TEMP;

    @Column(name = "processing_status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private AssetProcessingStatus processingStatus = AssetProcessingStatus.READY;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Asset(
            User uploader,
            AssetType assetType,
            String originalFilename,
            String storageKey,
            String url,
            String mimeType,
            Long sizeBytes
    ) {
        this.uploader = uploader;
        this.assetType = assetType;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.url = url;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }

    public static Asset createImage(
            User uploader,
            String originalFilename,
            String storageKey,
            String url,
            String mimeType,
            Long sizeBytes
    ) {
        return new Asset(
                uploader,
                AssetType.IMAGE,
                originalFilename,
                storageKey,
                url,
                mimeType,
                sizeBytes
        );
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
