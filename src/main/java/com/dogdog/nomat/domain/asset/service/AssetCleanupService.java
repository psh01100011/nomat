package com.dogdog.nomat.domain.asset.service;

import com.dogdog.nomat.domain.asset.config.AssetCleanupProperties;
import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
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
