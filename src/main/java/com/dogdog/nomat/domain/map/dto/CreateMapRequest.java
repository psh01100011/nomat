package com.dogdog.nomat.domain.map.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateMapRequest(
        @NotBlank
        @Size(min = 2, max = 40, message = "invalid_map_title_length")
        String title,

        @NotNull
        Long categoryId,

        @NotBlank
        String questionType,

        Long thumbnailAssetId,

        @Size(max = 500, message = "invalid_request")
        String description,

        String visibility,

        @NotEmpty
        @Size(max = 300, message = "invalid_request")
        List<@Valid QuestionRequest> questions
) {

    public record QuestionRequest(
            @NotBlank
            @Size(max = 200, message = "invalid_question_prompt_length")
            String promptText,

            @Valid
            MediaRequest media,

            @NotEmpty
            @Size(max = 20, message = "invalid_answer_count")
            List<@NotBlank(message = "invalid_answer_length") @Size(max = 50, message = "invalid_answer_length") String> answers
    ) {
    }

    public record MediaRequest(
            @NotBlank
            String sourceType,

            Long assetId,

            @Size(max = 500, message = "invalid_youtube_url")
            String sourceUrl,

            Long startTimeMs,

            Long endTimeMs
    ) {
    }
}
