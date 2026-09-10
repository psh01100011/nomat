package com.dogdog.nomat.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModifyMapCommentRequest(
        @NotBlank
        @Size(max = 300, message = "comment_too_long")
        String content
) {
}
