package com.dogdog.nomat.domain.map.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record ModifyMapRequest(
        @NotNull
        Integer version,

        Map<String, Object> map,

        @Valid
        QuestionsRequest questions
) {

    public record QuestionsRequest(
            List<@Valid CreateQuestionRequest> create,

            List<@Valid UpdateQuestionRequest> update,

            List<Long> delete
    ) {
    }

    public record CreateQuestionRequest(
            String clientId,

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

    public record UpdateQuestionRequest(
            @NotNull
            Long questionId,

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

            @Size(max = 500)
            String sourceUrl,

            Long startTimeMs,

            Long endTimeMs
    ) {
    }
}
