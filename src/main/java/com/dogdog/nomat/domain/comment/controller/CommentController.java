package com.dogdog.nomat.domain.comment.controller;

import com.dogdog.nomat.domain.comment.dto.ModifyMapCommentRequest;
import com.dogdog.nomat.domain.comment.dto.ModifyMapCommentResponse;
import com.dogdog.nomat.domain.comment.service.CommentService;
import com.dogdog.nomat.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/comments")
public class CommentController {

    private final CommentService commentService;

    @PatchMapping("/{commentId}")
    public ApiResponse<ModifyMapCommentResponse> modifyMapComment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long commentId,
            @Valid @RequestBody ModifyMapCommentRequest request
    ) {
        Long userId = getUserId(jwt);
        return ApiResponse.of(
                "success_modify_comment",
                commentService.modifyMapComment(userId, commentId, request)
        );
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteMapComment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long commentId
    ) {
        Long userId = getUserId(jwt);
        commentService.deleteMapComment(userId, commentId);
        return ApiResponse.success("success_delete_comment");
    }

    private Long getUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return userId.longValue();
    }
}
