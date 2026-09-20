package com.dogdog.nomat.domain.auth.dto;

import com.dogdog.nomat.domain.emailverification.service.EmailAddressNormalizer;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
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
        @Size(min = 2, max = 12, message = "invalid_nickname_length")
        @Pattern(regexp = "^[가-힣A-Za-z0-9_]+$", message = "invalid_nickname_format")
        String nickname,

        @Email(message = "invalid_request")
        @Pattern(regexp = EmailAddressNormalizer.OPTIONAL_EMAIL_PATTERN, message = "invalid_email")
        @Size(max = 254, message = "invalid_request")
        String email,

        String emailVerificationToken
) {

    public SignupRequest(
            String loginId,
            String password,
            String passwordConfirm,
            String nickname,
            String email
    ) {
        this(loginId, password, passwordConfirm, nickname, email, null);
    }

    @AssertTrue
    public boolean isPasswordConfirmed() {
        return Objects.equals(password, passwordConfirm);
    }
}
