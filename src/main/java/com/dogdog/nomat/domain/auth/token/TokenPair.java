package com.dogdog.nomat.domain.auth.token;

public record TokenPair(
        String accessToken,
        String refreshToken
) {
}
