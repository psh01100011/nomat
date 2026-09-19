package com.dogdog.nomat.domain.asset.service;

import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.QuestionStatus;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrphanedAssetCleanupService {

    private final AssetRepository assetRepository;
    private final S3Client s3Client;
    private final AssetS3Properties s3Properties;

    @Transactional
    public CleanupOutcome cleanup(Long assetId) {
        Asset asset = assetRepository.findByIdForUpdate(assetId).orElse(null);
        if (asset == null || asset.getStatus() != AssetStatus.ORPHANED) {
            return CleanupOutcome.SKIPPED;
        }

        if (assetRepository.existsActiveReference(
                assetId,
                UserStatus.DELETED,
                MapStatus.DELETED,
                QuestionStatus.DELETED
        )) {
            asset.attach();
            log.info("event=orphaned_asset_restored assetId={}", assetId);
            return CleanupOutcome.RESTORED;
        }

        if (!deleteS3Object(asset)) {
            return CleanupOutcome.FAILED;
        }

        asset.delete();
        log.info(
                "event=orphaned_asset_deleted assetId={} assetType={} storageKey={}",
                assetId,
                asset.getAssetType(),
                asset.getStorageKey()
        );
        return CleanupOutcome.DELETED;
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
            log.warn(
                    "event=orphaned_asset_cleanup_failed assetId={} storageKey={}",
                    asset.getId(),
                    asset.getStorageKey(),
                    exception
            );
            return false;
        }
    }

    public enum CleanupOutcome {
        DELETED,
        RESTORED,
        FAILED,
        SKIPPED
    }
}
