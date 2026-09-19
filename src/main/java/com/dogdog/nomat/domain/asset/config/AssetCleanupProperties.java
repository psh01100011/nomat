package com.dogdog.nomat.domain.asset.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.asset.cleanup")
public class AssetCleanupProperties {

    private boolean enabled;

    @Positive
    private long tempRetentionHours;

    @Positive
    private long orphanRetentionHours;

    @Positive
    private int batchSize;

    @Positive
    private int scanBatchSize;

    @Positive
    private long initialDelayMs;

    @Positive
    private long fixedDelayMs;

    @Positive
    private long lockTtlMinutes;
}
