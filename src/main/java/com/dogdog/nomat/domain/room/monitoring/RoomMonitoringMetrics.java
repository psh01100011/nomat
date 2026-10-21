package com.dogdog.nomat.domain.room.monitoring;

import com.dogdog.nomat.domain.room.model.RoomDomainEventType;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.room.service.RoomGameTimerExecutionGuard.ExecutionStatus;
import com.dogdog.nomat.domain.room.service.RoomWebSocketSessionRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class RoomMonitoringMetrics {

    private final MeterRegistry meterRegistry;
    private final RoomRedisRepository roomRepository;
    private final RoomWebSocketSessionRegistry sessionRegistry;

    public RoomMonitoringMetrics(
            MeterRegistry meterRegistry,
            RoomRedisRepository roomRepository,
            RoomWebSocketSessionRegistry sessionRegistry
    ) {
        this.meterRegistry = meterRegistry;
        this.roomRepository = roomRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("nomat.rooms.current", roomRepository, RoomRedisRepository::countActiveRooms)
                .description("Current rooms that have not reached their inactivity expiration")
                .register(meterRegistry);
        Gauge.builder(
                        "nomat.websocket.sessions.local",
                        sessionRegistry,
                        RoomWebSocketSessionRegistry::localSessionCount
                )
                .description("WebSocket sessions connected to this backend instance")
                .register(meterRegistry);
    }

    public void recordEventPublished(RoomDomainEventType type, String outcome) {
        counter("nomat.room.events.published", type.name(), outcome).increment();
    }

    public void recordEventDelivered(String type, String outcome) {
        counter("nomat.room.events.delivered", normalizedType(type), outcome).increment();
    }

    public void recordTimerClaim(RoomGameTimer timer, Instant claimedAt) {
        long lagMs = Math.max(Duration.between(timer.dueAt(), claimedAt).toMillis(), 0);
        Timer.builder("nomat.room.game.timer.lag")
                .description("Delay between a game timer due time and worker claim")
                .tag("type", timer.type().name())
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(lagMs, TimeUnit.MILLISECONDS);
    }

    public void recordTimerExecution(RoomGameTimer timer, ExecutionStatus status) {
        Counter.builder("nomat.room.game.timer.executions")
                .description("Claimed game timer execution results")
                .tag("type", timer.type().name())
                .tag("status", status.name())
                .register(meterRegistry)
                .increment();
    }

    public void recordTimerFailure(RoomGameTimer timer, String stage) {
        Counter.builder("nomat.room.game.timer.failures")
                .description("Game timer failures by processing stage")
                .tag("type", timer.type().name())
                .tag("stage", stage)
                .register(meterRegistry)
                .increment();
    }

    private Counter counter(String name, String type, String outcome) {
        return Counter.builder(name)
                .description("Room events by fixed event type and processing outcome")
                .tag("type", type)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private String normalizedType(String type) {
        if (type == null) {
            return "UNKNOWN";
        }
        try {
            return RoomDomainEventType.valueOf(type).name();
        } catch (IllegalArgumentException exception) {
            return "UNKNOWN";
        }
    }
}
