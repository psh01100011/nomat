package com.dogdog.nomat.domain.map.config;

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
    private long initialDelayMs = 10000;

    @Positive
    private long fixedDelayMs = 30000;

    @Positive
    private long commandTimeoutSeconds = 300;

    private String ytDlpPath = "yt-dlp";

    private String ffmpegPath = "ffmpeg";

    private String outputBitrate = "128k";
}
