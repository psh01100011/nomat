package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuestionAnswer;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaSourceType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MapEditorResponse(
        Long mapId,
        int version,
        String status,
        String visibility,
        String title,
        Long categoryId,
        String questionType,
        Long thumbnailAssetId,
        String thumbnailUrl,
        String description,
        List<QuestionEditorResponse> questions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MapEditorResponse of(
            QuizMap map,
            List<Question> questions,
            Map<Long, List<QuestionAnswer>> answersByQuestionId,
            Map<Long, QuestionMedia> mediaByQuestionId
    ) {
        Asset thumbnailAsset = map.getThumbnailAsset();
        Category category = map.getCategory();
        return new MapEditorResponse(
                map.getId(),
                map.getVersion(),
                map.getStatus().name(),
                map.getVisibility().name(),
                map.getTitle(),
                category == null ? null : category.getId(),
                map.getQuestionType() == null ? null : map.getQuestionType().name(),
                thumbnailAsset == null ? null : thumbnailAsset.getId(),
                thumbnailAsset == null ? null : thumbnailAsset.getUrl(),
                map.getDescription(),
                questions.stream()
                        .map(question -> QuestionEditorResponse.of(
                                question,
                                answersByQuestionId.getOrDefault(question.getId(), List.of()),
                                mediaByQuestionId.get(question.getId())
                        ))
                        .toList(),
                map.getCreatedAt(),
                map.getUpdatedAt()
        );
    }

    public record QuestionEditorResponse(
            Long questionId,
            String promptText,
            MediaEditorResponse media,
            List<String> answers
    ) {

        private static QuestionEditorResponse of(
                Question question,
                List<QuestionAnswer> answers,
                QuestionMedia media
        ) {
            return new QuestionEditorResponse(
                    question.getId(),
                    question.getPromptText(),
                    media == null ? null : MediaEditorResponse.from(media),
                    answers.stream()
                            .map(QuestionAnswer::getAnswerText)
                            .toList()
            );
        }
    }

    public record MediaEditorResponse(
            Long mediaId,
            String sourceType,
            Long assetId,
            String assetUrl,
            String sourceUrl,
            Integer startTimeMs,
            Integer endTimeMs,
            Integer durationMs,
            String processingStatus,
            String failureMessage,
            String audioUrl
    ) {

        private static MediaEditorResponse from(QuestionMedia media) {
            Asset asset = media.getAsset();
            String assetUrl = asset == null ? null : asset.getUrl();
            return new MediaEditorResponse(
                    media.getId(),
                    media.getSourceType().name(),
                    asset == null ? null : asset.getId(),
                    assetUrl,
                    media.getSourceUrl(),
                    media.getStartTimeMs(),
                    media.getEndTimeMs(),
                    media.getDurationMs(),
                    media.getProcessingStatus().name(),
                    media.getFailureMessage(),
                    media.getSourceType() == QuestionMediaSourceType.YOUTUBE ? assetUrl : null
            );
        }
    }
}
