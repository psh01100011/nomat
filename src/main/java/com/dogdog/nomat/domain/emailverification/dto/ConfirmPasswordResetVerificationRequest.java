package com.dogdog.nomat.domain.emailverification.dto;

import com.dogdog.nomat.domain.emailverification.service.EmailAddressNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmPasswordResetVerificationRequest(
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
        @Pattern(regexp = "^\\d{6}$")
        String code
) {
}
