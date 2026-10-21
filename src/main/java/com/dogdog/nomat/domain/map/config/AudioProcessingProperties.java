package com.dogdog.nomat.domain.map.config;

import jakarta.validation.constraints.AssertTrue;
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
@ConfigurationProperties(prefix = "app.audio-processing")
public class AudioProcessingProperties {

    private boolean enabled;

    @Positive
    private int batchSize = 5;

    @Positive
    private int workerConcurrency = 2;

    @Positive
    private int youtubeMaxConcurrency = 4;

    @Positive
    private long youtubePermitLeaseSeconds = 120;

    @Positive
    private long youtubePermitRenewalMs = 30000;

    @Positive
    private long initialDelayMs = 10000;

    @Positive
    private long fixedDelayMs = 10000;

    @Positive
    private long commandTimeoutSeconds = 300;

    @Positive
    private long heartbeatIntervalMs = 20000;

    @Positive
    private long heartbeatTimeoutSeconds = 90;

    @Positive
    private long delayedThresholdMinutes = 30;

    @Positive
    private int maxRetryAttempts = 3;

    private String ytDlpPath = "yt-dlp";

    private String ffmpegPath = "ffmpeg";

    private String outputBitrate = "128k";

    @AssertTrue(message = "youtube permit renewal interval must be shorter than its lease")
    public boolean isYoutubePermitRenewalBeforeExpiry() {
        return youtubePermitRenewalMs < youtubePermitLeaseSeconds * 1000;
    }

    @AssertTrue(message = "audio heartbeat interval must be shorter than its timeout")
    public boolean isHeartbeatIntervalBeforeTimeout() {
        return heartbeatIntervalMs < heartbeatTimeoutSeconds * 1000;
    }
}
