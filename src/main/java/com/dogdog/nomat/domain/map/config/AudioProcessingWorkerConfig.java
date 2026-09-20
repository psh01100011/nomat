package com.dogdog.nomat.domain.map.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnProperty(prefix = "app.audio-processing", name = "enabled", havingValue = "true")
public class AudioProcessingWorkerConfig {

    @Bean(name = "audioProcessingTaskExecutor")
    public ThreadPoolTaskExecutor audioProcessingTaskExecutor(AudioProcessingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerConcurrency());
        executor.setMaxPoolSize(properties.getWorkerConcurrency());
        executor.setQueueCapacity(properties.getWorkerConcurrency());
        executor.setThreadNamePrefix("audio-processing-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
