package com.dogdog.nomat.domain.map.service;

public record AudioProcessingTask(
        Long jobId,
        Long mapId,
        Long questionId,
        Long questionMediaId,
        int attemptCount,
        long queueWaitMs,
        String sourceUrl,
        Integer startTimeMs,
        Integer endTimeMs,
        Integer durationMs
) {
}
