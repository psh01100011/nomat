package com.dogdog.nomat.domain.auth.dto;

public record GuestLoginResponse(
        Long userId,
        String userType,
        String nickname,
        String accessToken,
        String refreshToken
) {
}
