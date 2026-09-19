package com.dogdog.nomat.domain.map.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AudioProcessingMetricsTest {

    @Test
    void recordsBoundedAttemptAndTimingMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AudioProcessingMetrics metrics = new AudioProcessingMetrics(
                registry,
                mock(AudioProcessingJobRepository.class)
        );
        metrics.registerGauges();

        metrics.recordClaim(250);
        metrics.recordAttempt(
                AudioProcessingJobStatus.FAILED,
                AudioProcessingFailureCode.STORAGE_ERROR,
                1500
        );

        assertThat(registry.get("nomat.audio.jobs.attempts")
                .tags("outcome", "failed", "failure_code", "STORAGE_ERROR")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get("nomat.audio.jobs.queue.wait")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250);
        assertThat(registry.get("nomat.audio.jobs.processing")
                .tag("outcome", "failed")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1500);
    }
}
