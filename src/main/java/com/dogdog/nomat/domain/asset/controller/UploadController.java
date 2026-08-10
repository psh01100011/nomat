package com.dogdog.nomat.domain.asset.controller;

import com.dogdog.nomat.domain.asset.dto.UploadImageResponse;
import com.dogdog.nomat.domain.asset.service.AssetService;
import com.dogdog.nomat.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/uploads")
public class UploadController {

    private final AssetService assetService;

    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadImageResponse> uploadImage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam MultipartFile file
    ) {
        Long userId = getUserId(jwt);
        return ApiResponse.of("success_upload_image", assetService.uploadImage(userId, file));
    }

    private Long getUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return userId.longValue();
    }
}
