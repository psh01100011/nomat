package com.dogdog.nomat.domain.room.scheduler;

import com.dogdog.nomat.domain.room.config.RoomGameTimerProperties;
import com.dogdog.nomat.domain.room.model.ClaimedRoomGameTimer;
import com.dogdog.nomat.domain.room.monitoring.RoomMonitoringMetrics;
import com.dogdog.nomat.domain.room.repository.RoomGameTimerRepository;
import com.dogdog.nomat.domain.room.service.RoomGameProgressService;
import com.dogdog.nomat.domain.room.service.RoomGameTimerExecutionGuard;
import com.dogdog.nomat.domain.room.service.RoomGameTimerExecutionGuard.ExecutionStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomGameTimerScheduler {

    private final RoomGameTimerRepository roomGameTimerRepository;
    private final RoomGameProgressService roomGameProgressService;
    private final RoomGameTimerExecutionGuard executionGuard;
    private final RoomGameTimerProperties properties;
    private final RoomMonitoringMetrics metrics;

    @Scheduled(
            initialDelayString = "${app.room.game-timer.initial-delay-ms:1000}",
            fixedDelayString = "${app.room.game-timer.fixed-delay-ms:100}",
            scheduler = "roomGameTimerTaskScheduler"
    )
    public void processDueTimers() {
        Instant claimedAt = Instant.now();
        List<ClaimedRoomGameTimer> timers = roomGameTimerRepository.claimDue(
                claimedAt,
                properties.leaseDuration(),
                properties.getBatchSize()
        );
        for (ClaimedRoomGameTimer claimedTimer : timers) {
            metrics.recordTimerClaim(claimedTimer.timer(), claimedAt);
            ExecutionStatus executionStatus;
            try {
                executionStatus = executionGuard.execute(
                        claimedTimer.timer(),
                        () -> roomGameProgressService.executeTimer(claimedTimer.timer())
                );
            } catch (RuntimeException exception) {
                metrics.recordTimerFailure(claimedTimer.timer(), "execution");
                log.error(
                        "event=room_game_timer_execution_failed roomId={} questionIndex={} timerType={}",
                        claimedTimer.timer().roomId(),
                        claimedTimer.timer().questionIndex(),
                        claimedTimer.timer().type(),
                        exception
                );
                continue;
            }
            metrics.recordTimerExecution(claimedTimer.timer(), executionStatus);
            if (executionStatus == ExecutionStatus.BUSY) {
                continue;
            }

            try {
                roomGameTimerRepository.complete(claimedTimer);
            } catch (RuntimeException exception) {
                metrics.recordTimerFailure(claimedTimer.timer(), "completion");
                log.error(
                        "event=room_game_timer_completion_failed roomId={} questionIndex={} timerType={}",
                        claimedTimer.timer().roomId(),
                        claimedTimer.timer().questionIndex(),
                        claimedTimer.timer().type(),
                        exception
                );
            }
        }
    }

}
