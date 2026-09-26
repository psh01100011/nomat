package com.dogdog.nomat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.dogdog.nomat.domain.map.service.AudioExtractionCommandRunner;
import com.dogdog.nomat.domain.map.service.AudioProcessingJobService;
import com.dogdog.nomat.domain.map.service.AudioProcessingService;
import com.dogdog.nomat.domain.map.service.AudioProcessingTask;
import com.dogdog.nomat.domain.map.service.YoutubeAudioExtractionCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public final class AudioProcessingLoadWorkerProcess {

    private static final Duration START_TIMEOUT = Duration.ofSeconds(60);

    private AudioProcessingLoadWorkerProcess() {
    }

    public static void main(String[] args) throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                NomatApplication.class,
                FakeWorkerExternalServices.class
        ).web(WebApplicationType.SERVLET).run(args)) {
            Path readyFile = Path.of(requiredEnvironment("LOAD_WORKER_READY_FILE"));
            Path startFile = Path.of(requiredEnvironment("LOAD_WORKER_START_FILE"));
            Files.writeString(readyFile, "ready");
            awaitStart(startFile);

            AudioProcessingJobService jobService = context.getBean(AudioProcessingJobService.class);
            AudioProcessingService processingService = context.getBean(AudioProcessingService.class);
            int processedCount = processUntilQueueIsEmpty(jobService, processingService);
            System.out.println("AUDIO_LOAD_WORKER_RESULT processed=" + processedCount);
        }
    }

    private static int processUntilQueueIsEmpty(
            AudioProcessingJobService jobService,
            AudioProcessingService processingService
    ) {
        int processedCount = 0;
        while (true) {
            List<AudioProcessingTask> tasks = jobService.claimProcessableJobs(1);
            if (tasks.isEmpty()) {
                return processedCount;
            }
            processingService.processClaimedJob(tasks.getFirst());
            processedCount++;
        }
    }

    private static void awaitStart(Path startFile) throws Exception {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (!Files.exists(startFile)) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Timed out waiting for the load test start signal.");
            }
            Thread.sleep(50);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured.");
        }
        return value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class FakeWorkerExternalServices {

        @Bean
        @Primary
        AudioExtractionCommandRunner fakeWorkerAudioExtractionCommandRunner() {
            return new AudioExtractionCommandRunner() {
                @Override
                public void extractYoutubeSegment(YoutubeAudioExtractionCommand command) throws java.io.IOException {
                    Files.writeString(command.outputFile(), "test-audio");
                }
            };
        }

        @Bean
        @Primary
        S3Client fakeWorkerS3Client() {
            S3Client client = mock(S3Client.class);
            doAnswer(invocation -> PutObjectResponse.builder().build())
                    .when(client)
                    .putObject(any(PutObjectRequest.class), any(RequestBody.class));
            return client;
        }
    }
}
