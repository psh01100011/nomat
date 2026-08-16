package com.dogdog.nomat.domain.comment.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public record MapCommentListResponse(
        List<MapCommentResponse> comments,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static MapCommentListResponse from(Page<MapComment> comments, Long userId) {
        return new MapCommentListResponse(
                comments.getContent().stream()
                        .map(comment -> MapCommentResponse.from(comment, userId))
                        .toList(),
                comments.getNumber(),
                comments.getSize(),
                comments.getTotalElements(),
                comments.getTotalPages(),
                comments.hasNext()
        );
    }

    public record MapCommentResponse(
            Long commentId,
            String content,
            WriterResponse writer,
            boolean isMine,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {

        private static MapCommentResponse from(MapComment comment, Long userId) {
            User writer = comment.getWriter();
            return new MapCommentResponse(
                    comment.getId(),
                    comment.getContent(),
                    WriterResponse.from(writer),
                    userId != null && writer.getId().equals(userId),
                    comment.getCreatedAt(),
                    comment.getUpdatedAt()
            );
        }
    }

    public record WriterResponse(
            Long userId,
            String nickname,
            String profileImageUrl
    ) {

        private static WriterResponse from(User writer) {
            Asset profileImageAsset = writer.getProfileImageAsset();
            return new WriterResponse(
                    writer.getId(),
                    writer.getNickname(),
                    profileImageAsset == null ? null : profileImageAsset.getUrl()
            );
        }
    }
}
