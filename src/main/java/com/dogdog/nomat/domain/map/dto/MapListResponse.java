package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public record MapListResponse(
        List<MapSummaryResponse> maps,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static MapListResponse from(Page<QuizMap> maps) {
        return new MapListResponse(
                maps.getContent().stream()
                        .map(MapSummaryResponse::from)
                        .toList(),
                maps.getNumber(),
                maps.getSize(),
                maps.getTotalElements(),
                maps.getTotalPages(),
                maps.hasNext()
        );
    }

    public record MapSummaryResponse(
            Long mapId,
            String title,
            String status,
            String visibility,
            CategorySummaryResponse category,
            String questionType,
            String thumbnailUrl,
            CreatorSummaryResponse creator,
            int questionCount,
            long playCount,
            long likeCount,
            long favoriteCount,
            long commentCount,
            boolean liked,
            boolean favorited,
            LocalDateTime createdAt
    ) {

        private static MapSummaryResponse from(QuizMap map) {
            Asset thumbnailAsset = map.getThumbnailAsset();
            return new MapSummaryResponse(
                    map.getId(),
                    map.getTitle(),
                    map.getStatus().name(),
                    map.getVisibility().name(),
                    CategorySummaryResponse.from(map.getCategory()),
                    map.getQuestionType().name(),
                    thumbnailAsset == null ? null : thumbnailAsset.getUrl(),
                    CreatorSummaryResponse.from(map.getCreator()),
                    map.getQuestionCount(),
                    map.getPlayCount(),
                    map.getLikeCount(),
                    map.getFavoriteCount(),
                    map.getCommentCount(),
                    false,
                    false,
                    map.getCreatedAt()
            );
        }
    }

    public record CategorySummaryResponse(
            Long categoryId,
            String name
    ) {

        private static CategorySummaryResponse from(Category category) {
            return new CategorySummaryResponse(category.getId(), category.getName());
        }
    }

    public record CreatorSummaryResponse(
            Long userId,
            String nickname
    ) {

        private static CreatorSummaryResponse from(User creator) {
            return new CreatorSummaryResponse(creator.getId(), creator.getNickname());
        }
    }
}
