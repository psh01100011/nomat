package com.dogdog.nomat.domain.asset.scheduler;

import com.dogdog.nomat.domain.asset.service.AssetCleanupService;
import com.dogdog.nomat.domain.asset.service.AssetCleanupLock;
import com.dogdog.nomat.domain.asset.service.AssetCleanupCursor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.asset.cleanup", name = "enabled", havingValue = "true")
public class AssetCleanupScheduler {

    private final AssetCleanupService assetCleanupService;
    private final AssetCleanupLock assetCleanupLock;
    private final AssetCleanupCursor assetCleanupCursor;

    @Scheduled(
            initialDelayString = "${app.asset.cleanup.initial-delay-ms}",
            fixedDelayString = "${app.asset.cleanup.fixed-delay-ms}"
    )
    public void cleanupAssets() {
        boolean acquired = assetCleanupLock.executeIfAcquired(() -> {
            int tempDeletedCount = assetCleanupService.cleanupExpiredTempAssets();
            AssetCleanupService.OrphanScanSummary scanSummary =
                    assetCleanupService.scanUnreferencedAssets(assetCleanupCursor.read());
            assetCleanupCursor.save(scanSummary.nextCursor());
            AssetCleanupService.OrphanCleanupSummary orphanSummary =
                    assetCleanupService.cleanupExpiredOrphanedAssets();
            if (tempDeletedCount > 0
                    || scanSummary.orphanedCount() > 0
                    || orphanSummary.deletedCount() > 0
                    || orphanSummary.restoredCount() > 0
                    || orphanSummary.failedCount() > 0) {
                log.info(
                        "event=asset_cleanup_completed tempDeletedCount={} scannedCount={} orphanedCount={} "
                                + "scanCursor={} scanWrapped={} orphanDeletedCount={} restoredCount={} failedCount={}",
                        tempDeletedCount,
                        scanSummary.scannedCount(),
                        scanSummary.orphanedCount(),
                        scanSummary.nextCursor(),
                        scanSummary.wrapped(),
                        orphanSummary.deletedCount(),
                        orphanSummary.restoredCount(),
                        orphanSummary.failedCount()
                );
            }
        });
        if (!acquired) {
            log.debug("event=asset_cleanup_skipped reason=lock_not_acquired");
        }
    }
}
