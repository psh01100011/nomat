package com.dogdog.nomat.domain.room.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RoomAudioLoadFailureRequest(
        @NotNull
        Long questionId,

        @NotNull
        @Min(1)
        Integer questionNumber
) {
}
