package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.map.entity.QuizMap;
import java.time.LocalDateTime;

public record SaveMapDraftResponse(
        Long mapId,
        int version,
        String status,
        LocalDateTime savedAt
) {

    public static SaveMapDraftResponse of(QuizMap map, LocalDateTime savedAt) {
        return new SaveMapDraftResponse(
                map.getId(),
                map.getVersion(),
                map.getStatus().name(),
                savedAt
        );
    }
}
