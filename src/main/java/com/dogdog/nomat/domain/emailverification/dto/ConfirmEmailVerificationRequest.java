package com.dogdog.nomat.domain.emailverification.dto;

import com.dogdog.nomat.domain.emailverification.service.EmailAddressNormalizer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmEmailVerificationRequest(
        @NotBlank
        @Email(message = "invalid_email")
        @Pattern(regexp = EmailAddressNormalizer.EMAIL_PATTERN, message = "invalid_email")
        @Size(max = 254, message = "invalid_email")
        String email,

        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "invalid_email_verification_code")
        String code
) {
}
