package com.dogdog.nomat.domain.room.scheduler;

import com.dogdog.nomat.domain.room.config.RoomGameTimerProperties;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.repository.RoomGameTimerRepository;
import com.dogdog.nomat.domain.room.service.RoomGameProgressService;
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
    private final RoomGameTimerProperties properties;

    @Scheduled(
            initialDelayString = "${app.room.game-timer.initial-delay-ms:1000}",
            fixedDelayString = "${app.room.game-timer.fixed-delay-ms:100}",
            scheduler = "roomGameTimerTaskScheduler"
    )
    public void processDueTimers() {
        List<RoomGameTimer> timers = roomGameTimerRepository.claimDue(
                Instant.now(),
                properties.getBatchSize()
        );
        for (RoomGameTimer timer : timers) {
            try {
                roomGameProgressService.executeTimer(timer);
            } catch (RuntimeException exception) {
                log.error(
                        "event=room_game_timer_execution_failed roomId={} questionIndex={} timerType={}",
                        timer.roomId(), timer.questionIndex(), timer.type(), exception
                );
            }
        }
    }

}
