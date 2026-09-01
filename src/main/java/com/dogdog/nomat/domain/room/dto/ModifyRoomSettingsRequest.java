package com.dogdog.nomat.domain.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ModifyRoomSettingsRequest(
        String password,

        @Min(2)
        @Max(12)
        Integer maxPlayers,

        @Min(1)
        @Max(300)
        Integer selectedQuestionCount,

        @Min(5)
        @Max(300)
        Integer answerTimeLimitSeconds
) {
}
