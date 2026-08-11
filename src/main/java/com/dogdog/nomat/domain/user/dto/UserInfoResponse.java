package com.dogdog.nomat.domain.user.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.user.entity.User;

public record UserInfoResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {

    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getId(),
                user.getNickname(),
                getProfileImageUrl(user)
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
