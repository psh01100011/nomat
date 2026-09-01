package com.dogdog.nomat.domain.comment.dto;

import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.user.dto.UserSummaryResponse;
import java.time.LocalDateTime;

public record CreateMapCommentResponse(
        Long commentId,
        String content,
        UserSummaryResponse writer,
        LocalDateTime createdAt
) {

    public static CreateMapCommentResponse from(MapComment comment) {
        return new CreateMapCommentResponse(
                comment.getId(),
                comment.getContent(),
                UserSummaryResponse.from(comment.getWriter()),
                comment.getCreatedAt()
        );
    }
}
