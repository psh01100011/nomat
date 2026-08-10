package com.dogdog.nomat.domain.user.controller;

import com.dogdog.nomat.domain.user.dto.AvailabilityResponse;
import com.dogdog.nomat.domain.user.dto.ModifyMyInfoRequest;
import com.dogdog.nomat.domain.user.dto.MyInfoResponse;
import com.dogdog.nomat.domain.user.dto.UserInfoResponse;
import com.dogdog.nomat.domain.user.dto.VerifyPasswordRequest;
import com.dogdog.nomat.domain.user.service.UserService;
import com.dogdog.nomat.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<MyInfoResponse> getMyInfo(@AuthenticationPrincipal Jwt jwt) {
        Long userId = getUserId(jwt);
        return ApiResponse.of("success_get_my_info", userService.getMyInfo(userId));
    }

    @GetMapping("/login-id/availability")
    public ApiResponse<AvailabilityResponse> checkLoginId(@RequestParam String loginId) {
        return ApiResponse.of("success_check_login_id", userService.checkLoginId(loginId));
    }

    @GetMapping("/nickname/availability")
    public ApiResponse<AvailabilityResponse> checkNickname(@RequestParam String nickname) {
        return ApiResponse.of("success_check_nickname", userService.checkNickname(nickname));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserInfoResponse> getUserInfo(@PathVariable Long userId) {
        return ApiResponse.of("success_get_user_info", userService.getUserInfo(userId));
    }

    @PostMapping("/me/password/verification")
    public ApiResponse<Void> verifyPassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyPasswordRequest request
    ) {
        Long userId = getUserId(jwt);
        userService.verifyPassword(userId, request);
        return ApiResponse.success("success_verify_password");
    }

    @PatchMapping("/me")
    public ApiResponse<Void> modifyMyInfo(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ModifyMyInfoRequest request
    ) {
        Long userId = getUserId(jwt);
        userService.modifyMyInfo(userId, request);
        return ApiResponse.success("success_modify_info");
    }

    private Long getUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return userId.longValue();
    }
}
