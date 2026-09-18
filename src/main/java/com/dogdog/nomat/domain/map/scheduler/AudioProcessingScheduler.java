package com.dogdog.nomat.domain.map.scheduler;

import com.dogdog.nomat.domain.map.service.AudioProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.audio-processing", name = "enabled", havingValue = "true")
public class AudioProcessingScheduler {

    private final AudioProcessingService audioProcessingService;

    @Scheduled(
            initialDelayString = "${app.audio-processing.initial-delay-ms}",
            fixedDelayString = "${app.audio-processing.fixed-delay-ms}"
    )
    public void processAvailableAudioJobs() {
        int processedCount = audioProcessingService.processAvailableJobs();
        if (processedCount > 0) {
            log.info("Processed queued audio jobs. count={}", processedCount);
        }
    }
}
