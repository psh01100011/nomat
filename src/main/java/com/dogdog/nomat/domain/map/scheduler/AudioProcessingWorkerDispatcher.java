package com.dogdog.nomat.domain.map.scheduler;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.map.service.AudioProcessingJobService;
import com.dogdog.nomat.domain.map.service.AudioProcessingService;
import com.dogdog.nomat.domain.map.service.AudioProcessingTask;
import java.util.ArrayList;
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
    private final YoutubeRequestConcurrencyLimiter concurrencyLimiter;
    private final AudioProcessingHeartbeatManager heartbeatManager;

    private int activeWorkerCount;

    public AudioProcessingWorkerDispatcher(
            AudioProcessingJobService audioProcessingJobService,
            AudioProcessingService audioProcessingService,
            AudioProcessingProperties properties,
            @Qualifier("audioProcessingTaskExecutor") TaskExecutor taskExecutor,
            YoutubeRequestConcurrencyLimiter concurrencyLimiter,
            AudioProcessingHeartbeatManager heartbeatManager
    ) {
        this.audioProcessingJobService = audioProcessingJobService;
        this.audioProcessingService = audioProcessingService;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
        this.concurrencyLimiter = concurrencyLimiter;
        this.heartbeatManager = heartbeatManager;
    }

    public int dispatchAvailableJobs() {
        List<ClaimedTask> claimedTasks;
        synchronized (this) {
            int availableWorkers = properties.getWorkerConcurrency() - activeWorkerCount;
            if (availableWorkers <= 0) {
                return 0;
            }
            int claimSize = Math.min(properties.getBatchSize(), availableWorkers);
            List<YoutubeRequestConcurrencyLimiter.Permit> permits = acquirePermits(claimSize);
            if (permits.isEmpty()) {
                return 0;
            }
            List<AudioProcessingTask> tasks;
            try {
                tasks = audioProcessingJobService.claimProcessableJobs(permits.size());
            } catch (RuntimeException exception) {
                permits.forEach(YoutubeRequestConcurrencyLimiter.Permit::close);
                log.error("event=audio_job_dispatch_failed claimSize={}", permits.size(), exception);
                return 0;
            }
            for (int index = tasks.size(); index < permits.size(); index++) {
                permits.get(index).close();
            }
            claimedTasks = new ArrayList<>(tasks.size());
            for (int index = 0; index < tasks.size(); index++) {
                claimedTasks.add(new ClaimedTask(tasks.get(index), permits.get(index)));
            }
            activeWorkerCount += tasks.size();
        }

        claimedTasks.forEach(this::submit);
        return claimedTasks.size();
    }

    private List<YoutubeRequestConcurrencyLimiter.Permit> acquirePermits(int count) {
        List<YoutubeRequestConcurrencyLimiter.Permit> permits = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            try {
                var permit = concurrencyLimiter.tryAcquire();
                if (permit.isEmpty()) {
                    break;
                }
                permits.add(permit.get());
            } catch (RuntimeException exception) {
                log.error("event=youtube_permit_acquisition_failed", exception);
                break;
            }
        }
        return permits;
    }

    private void submit(ClaimedTask claimedTask) {
        AudioProcessingTask task = claimedTask.task();
        try {
            taskExecutor.execute(() -> {
                heartbeatManager.register(task);
                try {
                    audioProcessingService.processClaimedJob(task);
                } catch (RuntimeException exception) {
                    log.error(
                            "event=audio_worker_unexpected_failure jobId={} attempt={}",
                            task.jobId(),
                            task.attemptCount(),
                            exception
                    );
                    audioProcessingService.handleUnexpectedWorkerFailure(task, exception);
                } finally {
                    heartbeatManager.unregister(task);
                    claimedTask.permit().close();
                    workerCompleted();
                }
            });
        } catch (RuntimeException exception) {
            claimedTask.permit().close();
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

    private record ClaimedTask(
            AudioProcessingTask task,
            YoutubeRequestConcurrencyLimiter.Permit permit
    ) {
    }
}
