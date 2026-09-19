package com.dogdog.nomat.domain.asset.scheduler;

import com.dogdog.nomat.domain.asset.service.AssetCleanupService;
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

    @Scheduled(
            initialDelayString = "${app.asset.cleanup.initial-delay-ms}",
            fixedDelayString = "${app.asset.cleanup.fixed-delay-ms}"
    )
    public void cleanupExpiredTempAssets() {
        int cleanedCount = assetCleanupService.cleanupExpiredTempAssets();
        if (cleanedCount > 0) {
            log.info("event=expired_assets_cleaned count={}", cleanedCount);
        }
    }
}
