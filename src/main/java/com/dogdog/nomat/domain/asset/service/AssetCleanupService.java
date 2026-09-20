package com.dogdog.nomat.domain.asset.service;

import com.dogdog.nomat.domain.asset.config.AssetCleanupProperties;
import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.QuestionStatus;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssetCleanupService {

    private final AssetRepository assetRepository;
    private final S3Client s3Client;
    private final AssetS3Properties s3Properties;
    private final AssetCleanupProperties cleanupProperties;
    private final OrphanedAssetCleanupService orphanedAssetCleanupService;

    @Transactional
    public int cleanupExpiredTempAssets() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(cleanupProperties.getTempRetentionHours());
        List<Asset> expiredTempAssets = assetRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                AssetStatus.TEMP,
                cutoff,
                PageRequest.of(0, cleanupProperties.getBatchSize())
        );

        int cleanedCount = 0;
        for (Asset asset : expiredTempAssets) {
            if (deleteS3Object(asset)) {
                asset.delete();
                cleanedCount++;
            }
        }

        return cleanedCount;
    }

    @Transactional
    public OrphanScanSummary scanUnreferencedAssets(long afterAssetId) {
        List<Long> assetIds = assetRepository.findAssetIdsAfter(
                AssetStatus.ATTACHED,
                afterAssetId,
                PageRequest.of(0, cleanupProperties.getScanBatchSize())
        );
        boolean wrapped = false;
        if (assetIds.isEmpty() && afterAssetId > 0) {
            wrapped = true;
            assetIds = assetRepository.findAssetIdsAfter(
                    AssetStatus.ATTACHED,
                    0L,
                    PageRequest.of(0, cleanupProperties.getScanBatchSize())
            );
        }
        if (assetIds.isEmpty()) {
            return new OrphanScanSummary(0, 0, 0L, wrapped);
        }

        List<Asset> assets = assetRepository.findUnreferencedAssetsByIdIn(
                assetIds,
                AssetStatus.ATTACHED,
                UserStatus.DELETED,
                MapStatus.DELETED,
                QuestionStatus.DELETED
        );
        LocalDateTime orphanedAt = LocalDateTime.now();
        assets.forEach(asset -> asset.markOrphaned(orphanedAt));
        return new OrphanScanSummary(
                assetIds.size(),
                assets.size(),
                assetIds.getLast(),
                wrapped
        );
    }

    public OrphanCleanupSummary cleanupExpiredOrphanedAssets() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(cleanupProperties.getOrphanRetentionHours());
        List<Long> assetIds = assetRepository.findOrphanedAssetIdsBefore(
                AssetStatus.ORPHANED,
                cutoff,
                PageRequest.of(0, cleanupProperties.getBatchSize())
        );

        int deletedCount = 0;
        int restoredCount = 0;
        int failedCount = 0;
        for (Long assetId : assetIds) {
            OrphanedAssetCleanupService.CleanupOutcome outcome = orphanedAssetCleanupService.cleanup(assetId);
            if (outcome == OrphanedAssetCleanupService.CleanupOutcome.DELETED) {
                deletedCount++;
            } else if (outcome == OrphanedAssetCleanupService.CleanupOutcome.RESTORED) {
                restoredCount++;
            } else if (outcome == OrphanedAssetCleanupService.CleanupOutcome.FAILED) {
                failedCount++;
            }
        }
        return new OrphanCleanupSummary(deletedCount, restoredCount, failedCount);
    }

    public record OrphanCleanupSummary(int deletedCount, int restoredCount, int failedCount) {
    }

    public record OrphanScanSummary(int scannedCount, int orphanedCount, long nextCursor, boolean wrapped) {
    }

    private boolean deleteS3Object(Asset asset) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(asset.getStorageKey())
                .build();

        try {
            s3Client.deleteObject(request);
            return true;
        } catch (SdkException exception) {
            log.warn("event=expired_asset_cleanup_failed assetId={} storageKey={}",
                    asset.getId(),
                    asset.getStorageKey(),
                    exception
            );
            return false;
        }
    }
}
