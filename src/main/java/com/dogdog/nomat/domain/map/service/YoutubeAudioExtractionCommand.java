package com.dogdog.nomat.domain.map.service;

import java.nio.file.Path;

public record YoutubeAudioExtractionCommand(
        String sourceUrl,
        Integer startTimeMs,
        Integer durationMs,
        Path outputFile
) {
}
