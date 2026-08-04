package com.dogdog.nomat.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public record SignupRequest(
        @NotBlank
        @Size(min = 4, max = 20)
        @Pattern(regexp = "^[A-Za-z0-9_]+$")
        String loginId,

        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$")
        String password,

        @NotBlank
        String passwordConfirm,

        @NotBlank
        @Size(min = 2, max = 20)
        String nickname,

        @Size(max = 500)
        String profileImageUrl
) {

    @AssertTrue
    public boolean isPasswordConfirmed() {
        return Objects.equals(password, passwordConfirm);
    }
}
