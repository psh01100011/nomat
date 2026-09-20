package com.dogdog.nomat.domain.emailverification.dto;

public record EmailVerificationConfirmedResponse(
        String emailVerificationToken,
        long expiresInSeconds
) {
}
