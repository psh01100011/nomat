package com.dogdog.nomat.domain.user.controller;

import com.dogdog.nomat.domain.user.dto.MyInfoResponse;
import com.dogdog.nomat.domain.user.service.UserService;
import com.dogdog.nomat.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private Long getUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return userId.longValue();
    }
}
