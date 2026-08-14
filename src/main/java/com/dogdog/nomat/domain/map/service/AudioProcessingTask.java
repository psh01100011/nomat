package com.dogdog.nomat.domain.map.service;

public record AudioProcessingTask(
        Long jobId,
        Long questionMediaId,
        String sourceUrl,
        Integer startTimeMs,
        Integer endTimeMs,
        Integer durationMs
) {
}
