package com.dogdog.nomat.domain.user.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;

public record UserSummaryResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean deleted
) {

    private static final String DELETED_USER_NICKNAME = "탈퇴한 사용자";

    public static UserSummaryResponse from(User user) {
        if (user.getStatus() == UserStatus.DELETED) {
            return new UserSummaryResponse(user.getId(), DELETED_USER_NICKNAME, null, true);
        }

        Asset profileImageAsset = user.getProfileImageAsset();
        return new UserSummaryResponse(
                user.getId(),
                user.getNickname(),
                profileImageAsset == null ? null : profileImageAsset.getUrl(),
                false
        );
    }
}
