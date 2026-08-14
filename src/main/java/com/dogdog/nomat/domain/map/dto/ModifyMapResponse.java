package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.map.entity.QuizMap;
import java.time.LocalDateTime;
import java.util.List;

public record ModifyMapResponse(
        Long mapId,
        int version,
        List<CreatedQuestionResponse> createdQuestions,
        LocalDateTime updatedAt
) {

    public static ModifyMapResponse of(
            QuizMap map,
            List<CreatedQuestionResponse> createdQuestions,
            LocalDateTime updatedAt
    ) {
        return new ModifyMapResponse(
                map.getId(),
                map.getVersion(),
                createdQuestions,
                updatedAt
        );
    }

    public record CreatedQuestionResponse(
            String clientId,
            Long questionId
    ) {
    }
}
