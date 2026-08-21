package com.dogdog.nomat.domain.comment.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.user.entity.User;
import java.time.LocalDateTime;

public record CreateMapCommentResponse(
        Long commentId,
        String content,
        WriterResponse writer,
        LocalDateTime createdAt
) {

    public static CreateMapCommentResponse from(MapComment comment) {
        return new CreateMapCommentResponse(
                comment.getId(),
                comment.getContent(),
                WriterResponse.from(comment.getWriter()),
                comment.getCreatedAt()
        );
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
