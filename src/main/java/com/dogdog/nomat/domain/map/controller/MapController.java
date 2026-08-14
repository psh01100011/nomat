package com.dogdog.nomat.domain.map.controller;

import com.dogdog.nomat.domain.map.dto.CreateMapRequest;
import com.dogdog.nomat.domain.map.dto.CreateMapResponse;
import com.dogdog.nomat.domain.map.dto.MapEditorResponse;
import com.dogdog.nomat.domain.map.dto.MapListResponse;
import com.dogdog.nomat.domain.map.service.MapService;
import com.dogdog.nomat.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateMapResponse> createMap(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMapRequest request
    ) {
        Long userId = getUserId(jwt);
        return ApiResponse.of("success_create_map", mapService.createMap(userId, request));
    }

    @GetMapping("/{mapId}/editor")
    public ApiResponse<MapEditorResponse> getMapEditor(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mapId
    ) {
        Long userId = getUserId(jwt);
        return ApiResponse.of("success_get_map_editor", mapService.getMapEditor(userId, mapId));
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

    private Long getUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return userId.longValue();
    }

    private Long getUserIdOrNull(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        return getUserId(jwt);
    }
}
