package com.dogdog.nomat.domain.emailverification.dto;

public record EmailVerificationSentResponse(
        long expiresInSeconds,
        long resendAvailableInSeconds
) {
}
