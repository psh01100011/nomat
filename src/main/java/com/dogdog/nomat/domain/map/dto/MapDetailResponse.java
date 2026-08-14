package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.user.entity.User;
import java.time.LocalDateTime;

public record MapDetailResponse(
        Long mapId,
        String title,
        CategoryDetailResponse category,
        String questionType,
        String thumbnailUrl,
        String description,
        CreatorDetailResponse creator,
        int questionCount,
        long playCount,
        long likeCount,
        long favoriteCount,
        long commentCount,
        boolean liked,
        boolean favorited,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MapDetailResponse from(QuizMap map, boolean liked, boolean favorited) {
        Asset thumbnailAsset = map.getThumbnailAsset();
        return new MapDetailResponse(
                map.getId(),
                map.getTitle(),
                CategoryDetailResponse.from(map.getCategory()),
                map.getQuestionType().name(),
                thumbnailAsset == null ? null : thumbnailAsset.getUrl(),
                map.getDescription(),
                CreatorDetailResponse.from(map.getCreator()),
                map.getQuestionCount(),
                map.getPlayCount(),
                map.getLikeCount(),
                map.getFavoriteCount(),
                map.getCommentCount(),
                liked,
                favorited,
                map.getCreatedAt(),
                map.getUpdatedAt()
        );
    }

    public record CategoryDetailResponse(
            Long categoryId,
            String name
    ) {

        private static CategoryDetailResponse from(Category category) {
            return new CategoryDetailResponse(category.getId(), category.getName());
        }
    }

    public record CreatorDetailResponse(
            Long userId,
            String nickname,
            String profileImageUrl
    ) {

        private static CreatorDetailResponse from(User creator) {
            Asset profileImageAsset = creator.getProfileImageAsset();
            return new CreatorDetailResponse(
                    creator.getId(),
                    creator.getNickname(),
                    profileImageAsset == null ? null : profileImageAsset.getUrl()
            );
        }
    }
}
