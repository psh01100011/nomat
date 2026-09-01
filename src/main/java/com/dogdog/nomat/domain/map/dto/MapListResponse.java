package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.user.dto.UserSummaryResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
        return from(maps, Set.of(), Set.of());
    }

    public static MapListResponse from(Page<QuizMap> maps, Set<Long> likedMapIds, Set<Long> favoritedMapIds) {
        return new MapListResponse(
                maps.getContent().stream()
                        .map(map -> MapSummaryResponse.from(
                                map,
                                likedMapIds.contains(map.getId()),
                                favoritedMapIds.contains(map.getId())
                        ))
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
            UserSummaryResponse creator,
            int questionCount,
            long playCount,
            long likeCount,
            long favoriteCount,
            long commentCount,
            boolean liked,
            boolean favorited,
            LocalDateTime createdAt
    ) {

        private static MapSummaryResponse from(QuizMap map, boolean liked, boolean favorited) {
            Asset thumbnailAsset = map.getThumbnailAsset();
            return new MapSummaryResponse(
                    map.getId(),
                    map.getTitle(),
                    map.getStatus().name(),
                    map.getVisibility().name(),
                    CategorySummaryResponse.from(map.getCategory()),
                    map.getQuestionType() == null ? null : map.getQuestionType().name(),
                    thumbnailAsset == null ? null : thumbnailAsset.getUrl(),
                    UserSummaryResponse.from(map.getCreator()),
                    map.getQuestionCount(),
                    map.getPlayCount(),
                    map.getLikeCount(),
                    map.getFavoriteCount(),
                    map.getCommentCount(),
                    liked,
                    favorited,
                    map.getCreatedAt()
            );
        }
    }

    public record CategorySummaryResponse(
            Long categoryId,
            String name
    ) {

        private static CategorySummaryResponse from(Category category) {
            if (category == null) {
                return null;
            }

            return new CategorySummaryResponse(category.getId(), category.getName());
        }
    }

}
