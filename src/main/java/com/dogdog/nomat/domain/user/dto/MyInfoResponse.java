package com.dogdog.nomat.domain.user.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.user.entity.User;

public record MyInfoResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {

    public static MyInfoResponse from(User user) {
        return new MyInfoResponse(
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
