package com.dogdog.nomat.domain.map.scheduler;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.map.service.AudioProcessingJobService;
import com.dogdog.nomat.domain.map.service.AudioProcessingService;
import com.dogdog.nomat.domain.map.service.AudioProcessingTask;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.audio-processing", name = "enabled", havingValue = "true")
public class AudioProcessingWorkerDispatcher {

    private final AudioProcessingJobService audioProcessingJobService;
    private final AudioProcessingService audioProcessingService;
    private final AudioProcessingProperties properties;
    private final TaskExecutor taskExecutor;

    private int activeWorkerCount;

    public AudioProcessingWorkerDispatcher(
            AudioProcessingJobService audioProcessingJobService,
            AudioProcessingService audioProcessingService,
            AudioProcessingProperties properties,
            @Qualifier("audioProcessingTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.audioProcessingJobService = audioProcessingJobService;
        this.audioProcessingService = audioProcessingService;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
    }

    public int dispatchAvailableJobs() {
        List<AudioProcessingTask> tasks;
        synchronized (this) {
            int availableWorkers = properties.getWorkerConcurrency() - activeWorkerCount;
            if (availableWorkers <= 0) {
                return 0;
            }
            int claimSize = Math.min(properties.getBatchSize(), availableWorkers);
            try {
                tasks = audioProcessingJobService.claimProcessableJobs(claimSize);
            } catch (RuntimeException exception) {
                log.error("event=audio_job_dispatch_failed claimSize={}", claimSize, exception);
                return 0;
            }
            activeWorkerCount += tasks.size();
        }

        tasks.forEach(this::submit);
        return tasks.size();
    }

    private void submit(AudioProcessingTask task) {
        try {
            taskExecutor.execute(() -> {
                try {
                    audioProcessingService.processClaimedJob(task);
                } finally {
                    workerCompleted();
                }
            });
        } catch (RuntimeException exception) {
            synchronized (this) {
                activeWorkerCount--;
            }
            log.error("event=audio_worker_submission_failed jobId={}", task.jobId(), exception);
        }
    }

    private void workerCompleted() {
        synchronized (this) {
            activeWorkerCount--;
        }
        dispatchAvailableJobs();
    }
}
