package com.dogdog.nomat.domain.map.controller;

import com.dogdog.nomat.domain.comment.dto.CreateMapCommentRequest;
import com.dogdog.nomat.domain.comment.dto.CreateMapCommentResponse;
import com.dogdog.nomat.domain.comment.dto.MapCommentListResponse;
import com.dogdog.nomat.domain.comment.service.CommentService;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUser;
import com.dogdog.nomat.domain.map.dto.AudioProcessingRetryResponse;
import com.dogdog.nomat.domain.map.dto.CreateMapRequest;
import com.dogdog.nomat.domain.map.dto.CreateMapResponse;
import com.dogdog.nomat.domain.map.dto.MapDetailResponse;
import com.dogdog.nomat.domain.map.dto.MapEditorResponse;
import com.dogdog.nomat.domain.map.dto.MapFavoriteResponse;
import com.dogdog.nomat.domain.map.dto.MapLikeResponse;
import com.dogdog.nomat.domain.map.dto.MapListResponse;
import com.dogdog.nomat.domain.map.dto.ModifyMapRequest;
import com.dogdog.nomat.domain.map.dto.ModifyMapResponse;
import com.dogdog.nomat.domain.map.dto.SaveMapDraftRequest;
import com.dogdog.nomat.domain.map.dto.SaveMapDraftResponse;
import com.dogdog.nomat.domain.map.service.MapService;
import com.dogdog.nomat.domain.report.dto.ReportMapRequest;
import com.dogdog.nomat.domain.report.dto.ReportMapResponse;
import com.dogdog.nomat.domain.report.service.ReportService;
import com.dogdog.nomat.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/maps")
public class MapController {

    private final MapService mapService;
    private final CommentService commentService;
    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateMapResponse> createMap(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMapRequest request
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of("success_create_map", mapService.createMap(userId, request));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SaveMapDraftResponse> saveMapDraft(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SaveMapDraftRequest request
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of("success_save_map_draft", mapService.saveMapDraft(userId, request));
    }

    @PostMapping("/{mapId}/like")
    public ApiResponse<MapLikeResponse> likeMap(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of("success_like_map", mapService.likeMap(userId, mapId));
    }

    @DeleteMapping("/{mapId}/like")
    public ApiResponse<MapLikeResponse> unlikeMap(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of("success_unlike_map", mapService.unlikeMap(userId, mapId));
    }

    @PostMapping("/{mapId}/favorite")
    public ApiResponse<MapFavoriteResponse> favoriteMap(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of("success_favorite_map", mapService.favoriteMap(userId, mapId));
    }

    @DeleteMapping("/{mapId}/favorite")
    public ApiResponse<MapFavoriteResponse> unfavoriteMap(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of("success_unfavorite_map", mapService.unfavoriteMap(userId, mapId));
    }

    @PostMapping("/{mapId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportMapResponse> reportMap(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId,
            @Valid @RequestBody ReportMapRequest request
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of(
                "success_report_map",
                reportService.reportMap(userId, mapId, request)
        );
    }

    @PatchMapping("/{mapId}")
    public ApiResponse<ModifyMapResponse> modifyMap(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId,
            @Valid @RequestBody ModifyMapRequest request
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of("success_modify_map", mapService.modifyMap(userId, mapId, request));
    }

    @PostMapping("/{mapId}/audio-processing/retry")
    public ApiResponse<AudioProcessingRetryResponse> retryAudioProcessing(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of(
                "success_retry_audio_processing",
                mapService.retryAudioProcessing(userId, mapId)
        );
    }

    @DeleteMapping("/{mapId}")
    public ApiResponse<Void> deleteMap(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        mapService.deleteMap(userId, mapId);
        return ApiResponse.success("success_delete_map");
    }

    @GetMapping("/{mapId}/editor")
    public ApiResponse<MapEditorResponse> getMapEditor(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of("success_get_map_editor", mapService.getMapEditor(userId, mapId));
    }

    @GetMapping("/{mapId}")
    public ApiResponse<MapDetailResponse> getMap(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId
    ) {
        Long userId = getUserIdOrNull(jwt);
        return ApiResponse.of("success_get_map", mapService.getMap(userId, mapId));
    }

    @GetMapping
    public ApiResponse<MapListResponse> getMaps(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String questionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) Long creatorId
    ) {
        Long userId = getUserIdOrNull(jwt);
        return ApiResponse.of(
                "success_get_maps",
                mapService.getMaps(userId, keyword, categoryId, questionType, page, size, sort, creatorId)
        );
    }

    @GetMapping("/{mapId}/comments")
    public ApiResponse<MapCommentListResponse> getMapComments(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort
    ) {
        Long userId = getUserIdOrNull(jwt);
        return ApiResponse.of(
                "success_get_map_comments",
                commentService.getMapComments(userId, mapId, page, size, sort)
        );
    }

    @PostMapping("/{mapId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateMapCommentResponse> createMapComment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId,
            @Valid @RequestBody CreateMapCommentRequest request
    ) {
        Long userId = AuthenticatedUser.from(jwt).requireMemberId();
        return ApiResponse.of(
                "success_create_comment",
                commentService.createMapComment(userId, mapId, request)
        );
    }

    private Long getUserIdOrNull(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        if (user.isGuest()) {
            return null;
        }

        return user.userId();
    }
}
