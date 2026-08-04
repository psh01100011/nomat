package com.dogdog.nomat.domain.auth.dto;

public record LoginResponse(
        Long userId,
        String accessToken,
        String refreshToken
) {
}
