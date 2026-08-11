package com.dogdog.nomat.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyPasswordRequest(
        @NotBlank
        @Size(min = 8, max = 64)
        String password
) {
}
