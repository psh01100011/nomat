package com.dogdog.nomat.domain.map.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveMapDraftRequest(
        @Size(max = 40, message = "invalid_map_title_length")
        String title,

        Long categoryId,

        String questionType,

        Long thumbnailAssetId,

        @Size(max = 500, message = "invalid_request")
        String description,

        String visibility,

        @Size(max = 300, message = "invalid_request")
        List<@Valid QuestionRequest> questions
) {

    public record QuestionRequest(
            @Size(max = 200, message = "invalid_question_prompt_length")
            String promptText,

            @Valid
            MediaRequest media,

            @Size(max = 20, message = "invalid_answer_count")
            List<@Size(max = 50, message = "invalid_answer_length") String> answers
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
