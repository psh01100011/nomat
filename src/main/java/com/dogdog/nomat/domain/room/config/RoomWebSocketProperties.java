package com.dogdog.nomat.domain.room.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.room.websocket")
public class RoomWebSocketProperties {

    @Min(5)
    private long sessionTtlMinutes = 10;

    @NotEmpty
    private List<String> allowedOrigins = List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    );

    public Duration sessionTtl() {
        return Duration.ofMinutes(sessionTtlMinutes);
    }
}
