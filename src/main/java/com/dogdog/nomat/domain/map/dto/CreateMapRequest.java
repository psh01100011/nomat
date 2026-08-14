package com.dogdog.nomat.domain.map.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateMapRequest(
        @NotBlank
        @Size(max = 100)
        String title,

        @NotNull
        Long categoryId,

        @NotBlank
        String questionType,

        Long thumbnailAssetId,

        String description,

        String visibility,

        @NotEmpty
        List<@Valid QuestionRequest> questions
) {

    public record QuestionRequest(
            @NotBlank
            String promptText,

            @Valid
            MediaRequest media,

            @NotEmpty
            List<@NotBlank @Size(max = 255) String> answers
    ) {
    }

    public record MediaRequest(
            @NotBlank
            String sourceType,

            Long assetId,

            @Size(max = 500)
            String sourceUrl,

            Long startTimeMs,

            Long endTimeMs
    ) {
    }
}
