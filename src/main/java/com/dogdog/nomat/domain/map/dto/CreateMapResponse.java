package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.map.entity.QuizMap;

public record CreateMapResponse(
        Long mapId
) {

    public static CreateMapResponse from(QuizMap map) {
        return new CreateMapResponse(map.getId());
    }
}
