package com.dogdog.nomat.domain.comment.dto;

import com.dogdog.nomat.domain.comment.entity.MapComment;
import java.time.LocalDateTime;

public record ModifyMapCommentResponse(
        Long commentId,
        String content,
        LocalDateTime updatedAt
) {

    public static ModifyMapCommentResponse from(MapComment comment) {
        return new ModifyMapCommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getUpdatedAt()
        );
    }
}
