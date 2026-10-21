package com.dogdog.nomat.domain.map.scheduler;

import com.dogdog.nomat.domain.map.service.AudioProcessingJobService;
import com.dogdog.nomat.domain.map.service.AudioProcessingTask;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.audio-processing", name = "enabled", havingValue = "true")
public class AudioProcessingHeartbeatManager {

    private final AudioProcessingJobService audioProcessingJobService;
    private final Map<Long, AudioProcessingTask> activeTasks = new ConcurrentHashMap<>();

    public void register(AudioProcessingTask task) {
        activeTasks.put(task.jobId(), task);
    }

    public void unregister(AudioProcessingTask task) {
        activeTasks.remove(task.jobId(), task);
    }

    @Scheduled(
            initialDelayString = "${app.audio-processing.heartbeat-interval-ms:20000}",
            fixedDelayString = "${app.audio-processing.heartbeat-interval-ms:20000}"
    )
    public void refreshActiveTaskHeartbeats() {
        List<AudioProcessingTask> snapshot = List.copyOf(activeTasks.values());
        if (snapshot.isEmpty()) {
            return;
        }

        try {
            audioProcessingJobService.refreshProcessingHeartbeats(snapshot);
        } catch (RuntimeException exception) {
            log.error("event=audio_job_heartbeat_refresh_failed activeTaskCount={}", snapshot.size(), exception);
        }
    }
}
