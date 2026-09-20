package com.dogdog.nomat.domain.map.monitoring;

import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class AudioProcessingMetrics {

    private static final List<AudioProcessingJobStatus> PROCESSABLE_STATUSES = List.of(
            AudioProcessingJobStatus.QUEUED,
            AudioProcessingJobStatus.RETRYING
    );

    private final MeterRegistry meterRegistry;
    private final AudioProcessingJobRepository audioProcessingJobRepository;

    public AudioProcessingMetrics(
            MeterRegistry meterRegistry,
            AudioProcessingJobRepository audioProcessingJobRepository
    ) {
        this.meterRegistry = meterRegistry;
        this.audioProcessingJobRepository = audioProcessingJobRepository;
    }

    @PostConstruct
    void registerGauges() {
        for (AudioProcessingJobStatus status : AudioProcessingJobStatus.values()) {
            Gauge.builder(
                            "nomat.audio.jobs.current",
                            audioProcessingJobRepository,
                            repository -> repository.countByStatus(status)
                    )
                    .description("Current audio processing jobs by status")
                    .tag("status", status.name())
                    .register(meterRegistry);
        }

        Gauge.builder(
                        "nomat.audio.jobs.oldest.age",
                        audioProcessingJobRepository,
                        repository -> ageSeconds(repository.findOldestCreatedAtByStatusIn(PROCESSABLE_STATUSES))
                )
                .description("Age in seconds of the oldest queued or retrying audio job")
                .tag("state", "processable")
                .baseUnit("seconds")
                .register(meterRegistry);

        Gauge.builder(
                        "nomat.audio.jobs.oldest.age",
                        audioProcessingJobRepository,
                        repository -> ageSeconds(
                                repository.findOldestStartedAtByStatus(AudioProcessingJobStatus.PROCESSING)
                        )
                )
                .description("Age in seconds of the oldest processing audio job")
                .tag("state", "processing")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    public void recordClaim(long queueWaitMs) {
        Timer.builder("nomat.audio.jobs.queue.wait")
                .description("Time from audio job creation to worker claim")
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(queueWaitMs, 0), TimeUnit.MILLISECONDS);
    }

    public void recordAttempt(
            AudioProcessingJobStatus resultStatus,
            AudioProcessingFailureCode failureCode,
            long durationMs
    ) {
        String outcome = resultStatus == AudioProcessingJobStatus.SUCCEEDED
                ? "succeeded"
                : resultStatus == AudioProcessingJobStatus.RETRYING ? "retrying" : "failed";
        String failure = failureCode == null ? "NONE" : failureCode.name();

        Counter.builder("nomat.audio.jobs.attempts")
                .description("Completed audio processing attempts by outcome")
                .tag("outcome", outcome)
                .tag("failure_code", failure)
                .register(meterRegistry)
                .increment();
        Timer.builder("nomat.audio.jobs.processing")
                .description("Audio processing attempt duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(durationMs, 0), TimeUnit.MILLISECONDS);
    }

    public void recordStaleRecovery(AudioProcessingJobStatus resultStatus) {
        Counter.builder("nomat.audio.jobs.stale.recoveries")
                .description("Stale processing jobs recovered by result status")
                .tag("status", resultStatus.name())
                .register(meterRegistry)
                .increment();
    }

    private double ageSeconds(LocalDateTime timestamp) {
        if (timestamp == null) {
            return 0;
        }
        return Math.max(Duration.between(timestamp, LocalDateTime.now()).toSeconds(), 0);
    }
}
