package com.dogdog.nomat.domain.user.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUser;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUserType;
import com.dogdog.nomat.domain.user.entity.User;
import java.time.LocalDateTime;

public record MyInfoResponse(
        Long userId,
        String userType,
        String nickname,
        String profileImageUrl,
        String email,
        boolean emailVerified,
        LocalDateTime emailVerifiedAt
) {

    public static MyInfoResponse from(User user) {
        return new MyInfoResponse(
                user.getId(),
                AuthenticatedUserType.MEMBER.name(),
                user.getNickname(),
                getProfileImageUrl(user),
                user.getEmail(),
                user.hasVerifiedEmail(),
                user.getEmailVerifiedAt()
        );
    }

    public static MyInfoResponse fromGuest(AuthenticatedUser user) {
        return new MyInfoResponse(
                user.userId(),
                AuthenticatedUserType.GUEST.name(),
                user.nickname(),
                null,
                null,
                false,
                null
        );
    }

    private static String getProfileImageUrl(User user) {
        Asset profileImageAsset = user.getProfileImageAsset();
        if (profileImageAsset == null) {
            return null;
        }

        return profileImageAsset.getUrl();
    }
}
