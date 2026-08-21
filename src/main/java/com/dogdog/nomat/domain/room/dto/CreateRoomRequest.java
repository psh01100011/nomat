package com.dogdog.nomat.domain.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotNull
        Long mapId,

        @NotBlank
        @Size(max = 100)
        String title,

        @Size(max = 100)
        String password,

        @NotNull
        @Min(2)
        @Max(50)
        Integer maxPlayers,

        @NotNull
        @Min(1)
        @Max(300)
        Integer selectedQuestionCount,

        @NotNull
        @Min(5)
        @Max(300)
        Integer answerTimeLimitSeconds,

        String timeLimitMode,

        Boolean audioRepeatEnabled,

        Boolean initialHintEnabled,

        @Min(0)
        @Max(300)
        Integer initialHintTriggerSeconds
) {
}
