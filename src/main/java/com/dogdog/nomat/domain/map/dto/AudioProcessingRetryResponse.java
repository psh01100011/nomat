package com.dogdog.nomat.domain.map.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AudioProcessingRetryResponse(
        Long mapId,
        List<Long> retriedQuestionIds,
        int retriedCount,
        LocalDateTime requestedAt
) {

    public static AudioProcessingRetryResponse of(
            Long mapId,
            List<Long> retriedQuestionIds,
            LocalDateTime requestedAt
    ) {
        return new AudioProcessingRetryResponse(
                mapId,
                retriedQuestionIds,
                retriedQuestionIds.size(),
                requestedAt
        );
    }
}
