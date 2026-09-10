package com.dogdog.nomat.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GuestLoginRequest(
        @NotBlank
        @Size(min = 2, max = 12, message = "invalid_nickname_length")
        @Pattern(regexp = "^[가-힣A-Za-z0-9_]+$", message = "invalid_nickname_format")
        String nickname
) {
}
