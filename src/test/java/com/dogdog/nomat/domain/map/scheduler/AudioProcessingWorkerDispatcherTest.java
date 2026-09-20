package com.dogdog.nomat.domain.map.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.map.service.AudioProcessingJobService;
import com.dogdog.nomat.domain.map.service.AudioProcessingService;
import com.dogdog.nomat.domain.map.service.AudioProcessingTask;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class AudioProcessingWorkerDispatcherTest {

    @Mock
    private AudioProcessingJobService audioProcessingJobService;

    @Mock
    private AudioProcessingService audioProcessingService;

    @Mock
    private YoutubeRequestConcurrencyLimiter concurrencyLimiter;

    @Mock
    private YoutubeRequestConcurrencyLimiter.Permit firstPermit;

    @Mock
    private YoutubeRequestConcurrencyLimiter.Permit secondPermit;

    @Mock
    private YoutubeRequestConcurrencyLimiter.Permit thirdPermit;

    @Test
    void limitsActiveWorkersAndRefillsAWorkerImmediatelyAfterCompletion() {
        AudioProcessingProperties properties = new AudioProcessingProperties();
        properties.setBatchSize(5);
        properties.setWorkerConcurrency(2);
        Queue<Runnable> submittedTasks = new ArrayDeque<>();
        TaskExecutor taskExecutor = submittedTasks::add;
        AudioProcessingWorkerDispatcher dispatcher = new AudioProcessingWorkerDispatcher(
                audioProcessingJobService,
                audioProcessingService,
                properties,
                taskExecutor,
                concurrencyLimiter
        );
        AudioProcessingTask first = task(1L);
        AudioProcessingTask second = task(2L);
        AudioProcessingTask third = task(3L);
        given(audioProcessingJobService.claimProcessableJobs(2)).willReturn(List.of(first, second));
        given(audioProcessingJobService.claimProcessableJobs(1))
                .willReturn(List.of(third), List.of());
        when(concurrencyLimiter.tryAcquire()).thenReturn(
                Optional.of(firstPermit),
                Optional.of(secondPermit),
                Optional.of(thirdPermit),
                Optional.of(firstPermit),
                Optional.empty()
        );

        assertThat(dispatcher.dispatchAvailableJobs()).isEqualTo(2);
        assertThat(submittedTasks).hasSize(2);
        assertThat(dispatcher.dispatchAvailableJobs()).isZero();
        verify(audioProcessingJobService, times(1)).claimProcessableJobs(2);

        submittedTasks.remove().run();

        verify(audioProcessingService).processClaimedJob(first);
        verify(audioProcessingJobService, times(1)).claimProcessableJobs(1);
        assertThat(submittedTasks).hasSize(2);
        assertThat(dispatcher.dispatchAvailableJobs()).isZero();

        submittedTasks.remove().run();
        submittedTasks.remove().run();

        verify(audioProcessingService).processClaimedJob(second);
        verify(audioProcessingService).processClaimedJob(third);
        verify(firstPermit, times(2)).close();
        verify(secondPermit).close();
        verify(thirdPermit).close();
    }

    private AudioProcessingTask task(Long jobId) {
        return new AudioProcessingTask(
                jobId,
                100L,
                20L,
                10L,
                1,
                500L,
                "https://youtube.com/watch?v=test",
                0,
                30_000,
                30_000
        );
    }
}
