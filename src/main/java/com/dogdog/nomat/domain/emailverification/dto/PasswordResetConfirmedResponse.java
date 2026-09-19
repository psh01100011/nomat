package com.dogdog.nomat.domain.emailverification.dto;

public record PasswordResetConfirmedResponse(
        String emailVerificationToken,
        long expiresInSeconds,
        String loginId
) {
}
