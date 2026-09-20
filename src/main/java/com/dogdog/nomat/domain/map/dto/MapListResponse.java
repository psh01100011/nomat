package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureType;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaProcessingStatus;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.user.dto.UserSummaryResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
        return from(maps, likedMapIds, favoritedMapIds, Map.of());
    }

    public static MapListResponse from(
            Page<QuizMap> maps,
            Set<Long> likedMapIds,
            Set<Long> favoritedMapIds,
            Map<Long, List<QuestionMedia>> mediaByMapId
    ) {
        return from(maps, likedMapIds, favoritedMapIds, mediaByMapId, null);
    }

    public static MapListResponse from(
            Page<QuizMap> maps,
            Set<Long> likedMapIds,
            Set<Long> favoritedMapIds,
            Map<Long, List<QuestionMedia>> mediaByMapId,
            LocalDateTime delayedBefore
    ) {
        return new MapListResponse(
                maps.getContent().stream()
                        .map(map -> MapSummaryResponse.from(
                                map,
                                likedMapIds.contains(map.getId()),
                                favoritedMapIds.contains(map.getId()),
                                mediaByMapId.get(map.getId()),
                                delayedBefore
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
            AudioProcessingSummaryResponse audioProcessing,
            LocalDateTime createdAt
    ) {

        private static MapSummaryResponse from(
                QuizMap map,
                boolean liked,
                boolean favorited,
                List<QuestionMedia> media,
                LocalDateTime delayedBefore
        ) {
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
                    AudioProcessingSummaryResponse.from(media, delayedBefore),
                    map.getCreatedAt()
            );
        }
    }

    public record AudioProcessingSummaryResponse(
            String status,
            int totalCount,
            int queuedCount,
            int processingCount,
            int retryingCount,
            int readyCount,
            int failedCount,
            int retryableFailedCount,
            int sourceFailureCount,
            boolean canRetryAll,
            LocalDateTime requestedAt,
            LocalDateTime lastUpdatedAt
    ) {

        private static AudioProcessingSummaryResponse from(
                List<QuestionMedia> media,
                LocalDateTime delayedBefore
        ) {
            if (media == null || media.isEmpty()) {
                return null;
            }

            int queuedCount = count(media, QuestionMediaProcessingStatus.QUEUED);
            int processingCount = count(media, QuestionMediaProcessingStatus.PROCESSING);
            int retryingCount = count(media, QuestionMediaProcessingStatus.RETRYING);
            int readyCount = count(media, QuestionMediaProcessingStatus.READY);
            int failedCount = count(media, QuestionMediaProcessingStatus.FAILED);
            int retryableFailedCount = (int) media.stream()
                    .filter(QuestionMedia::canRetry)
                    .count();
            int sourceFailureCount = (int) media.stream()
                    .filter(item -> item.getProcessingStatus() == QuestionMediaProcessingStatus.FAILED)
                    .filter(item -> item.getFailureCode() != null
                            && item.getFailureCode().getFailureType() == AudioProcessingFailureType.SOURCE)
                    .count();
            boolean delayed = isDelayed(media, delayedBefore);
            String status = overallStatus(queuedCount, processingCount, retryingCount, failedCount, delayed);
            LocalDateTime requestedAt = media.stream()
                    .map(QuestionMedia::getProcessingRequestedAt)
                    .filter(java.util.Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime lastUpdatedAt = media.stream()
                    .map(QuestionMedia::getUpdatedAt)
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            return new AudioProcessingSummaryResponse(
                    status,
                    media.size(),
                    queuedCount,
                    processingCount,
                    retryingCount,
                    readyCount,
                    failedCount,
                    retryableFailedCount,
                    sourceFailureCount,
                    retryableFailedCount > 0 && sourceFailureCount == 0,
                    requestedAt,
                    lastUpdatedAt
            );
        }

        private static int count(List<QuestionMedia> media, QuestionMediaProcessingStatus status) {
            return (int) media.stream()
                    .filter(item -> item.getProcessingStatus() == status)
                    .count();
        }

        private static boolean isDelayed(List<QuestionMedia> media, LocalDateTime delayedBefore) {
            if (delayedBefore == null) {
                return false;
            }

            return media.stream()
                    .filter(item -> item.getProcessingStatus() == QuestionMediaProcessingStatus.QUEUED
                            || item.getProcessingStatus() == QuestionMediaProcessingStatus.PROCESSING
                            || item.getProcessingStatus() == QuestionMediaProcessingStatus.RETRYING)
                    .map(QuestionMedia::getProcessingRequestedAt)
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(requestedAt -> !requestedAt.isAfter(delayedBefore));
        }

        private static String overallStatus(
                int queuedCount,
                int processingCount,
                int retryingCount,
                int failedCount,
                boolean delayed
        ) {
            if (failedCount > 0) return QuestionMediaProcessingStatus.FAILED.name();
            if (delayed) return "DELAYED";
            if (retryingCount > 0) return QuestionMediaProcessingStatus.RETRYING.name();
            if (processingCount > 0) return QuestionMediaProcessingStatus.PROCESSING.name();
            if (queuedCount > 0) return QuestionMediaProcessingStatus.QUEUED.name();
            return QuestionMediaProcessingStatus.READY.name();
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
