package com.dogdog.nomat.domain.room.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.room.game-timer")
public class RoomGameTimerProperties {

    @Min(1)
    private int batchSize = 100;

    @Min(1000)
    private long leaseDurationMs = 5000;

    public Duration leaseDuration() {
        return Duration.ofMillis(leaseDurationMs);
    }
}
