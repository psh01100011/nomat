package com.dogdog.nomat.domain.auth.model;

import com.dogdog.nomat.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

public record AuthenticatedUser(
        Long userId,
        AuthenticatedUserType userType,
        String nickname
) {

    public static AuthenticatedUser from(Jwt jwt) {
        if (jwt == null) {
            throw invalidToken();
        }

        Number userId = jwt.getClaim("userId");
        if (userId == null) {
            throw invalidToken();
        }

        AuthenticatedUserType userType = userType(jwt);
        String nickname = jwt.getClaimAsString("nickname");
        if (userType == AuthenticatedUserType.GUEST && !StringUtils.hasText(nickname)) {
            throw invalidToken();
        }

        return new AuthenticatedUser(userId.longValue(), userType, nickname);
    }

    public boolean isMember() {
        return userType == AuthenticatedUserType.MEMBER;
    }

    public boolean isGuest() {
        return userType == AuthenticatedUserType.GUEST;
    }

    public void requireMember() {
        if (isGuest()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "member_only");
        }
    }

    public void requireGuest() {
        if (isMember()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "guest_only");
        }
    }

    private static AuthenticatedUserType userType(Jwt jwt) {
        String userTypeValue = jwt.getClaimAsString("userType");
        if (!StringUtils.hasText(userTypeValue)) {
            return AuthenticatedUserType.MEMBER;
        }

        try {
            return AuthenticatedUserType.valueOf(userTypeValue);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private static BusinessException invalidToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token");
    }
}
