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
@ConfigurationProperties(prefix = "app.room.lifecycle")
public class RoomLifecycleProperties {

    @Min(5)
    private long inactivityTtlMinutes = 15;

    @Min(5)
    private long waitingMaxLifetimeMinutes = 30;

    public Duration inactivityTtl() {
        return Duration.ofMinutes(inactivityTtlMinutes);
    }

    public Duration waitingMaxLifetime() {
        return Duration.ofMinutes(waitingMaxLifetimeMinutes);
    }

    public Duration timerTrackingTtl() {
        return inactivityTtl().plus(waitingMaxLifetime());
    }
}
