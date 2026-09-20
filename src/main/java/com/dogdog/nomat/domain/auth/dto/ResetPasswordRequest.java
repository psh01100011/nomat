package com.dogdog.nomat.domain.auth.dto;

import com.dogdog.nomat.domain.emailverification.service.EmailAddressNormalizer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public record ResetPasswordRequest(
        @NotBlank
        @Size(min = 4, max = 20)
        @Pattern(regexp = "^[A-Za-z0-9_]+$")
        String loginId,

        @NotBlank
        @Email(message = "invalid_email")
        @Pattern(regexp = EmailAddressNormalizer.EMAIL_PATTERN, message = "invalid_email")
        @Size(max = 254, message = "invalid_email")
        String email,

        @NotBlank
        String emailVerificationToken,

        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$")
        String password,

        @NotBlank
        String passwordConfirm
) {

    @AssertTrue
    public boolean isPasswordConfirmed() {
        return Objects.equals(password, passwordConfirm);
    }
}
