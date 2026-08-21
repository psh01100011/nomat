package com.dogdog.nomat.domain.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomChatMessageRequest(
        @NotBlank
        @Size(max = 300)
        String content
) {
}
