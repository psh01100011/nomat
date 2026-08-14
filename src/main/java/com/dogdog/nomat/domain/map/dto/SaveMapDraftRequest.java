package com.dogdog.nomat.domain.map.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveMapDraftRequest(
        @Size(max = 100)
        String title,

        Long categoryId,

        String questionType,

        Long thumbnailAssetId,

        String description,

        String visibility,

        List<@Valid QuestionRequest> questions
) {

    public record QuestionRequest(
            String promptText,

            @Valid
            MediaRequest media,

            List<@Size(max = 255) String> answers
    ) {
    }

    public record MediaRequest(
            String sourceType,

            Long assetId,

            @Size(max = 500)
            String sourceUrl,

            Long startTimeMs,

            Long endTimeMs
    ) {
    }
}
