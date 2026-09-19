package com.dogdog.nomat.domain.auth.controller;

import com.dogdog.nomat.domain.auth.model.AuthenticatedUser;
import com.dogdog.nomat.domain.auth.dto.GuestLoginRequest;
import com.dogdog.nomat.domain.auth.dto.GuestLoginResponse;
import com.dogdog.nomat.domain.auth.dto.LoginRequest;
import com.dogdog.nomat.domain.auth.dto.LoginResponse;
import com.dogdog.nomat.domain.auth.dto.RefreshResponse;
import com.dogdog.nomat.domain.auth.dto.SignupRequest;
import com.dogdog.nomat.domain.auth.service.AuthService;
import com.dogdog.nomat.domain.emailverification.dto.ConfirmEmailVerificationRequest;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationConfirmedResponse;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationSentResponse;
import com.dogdog.nomat.domain.emailverification.dto.SendEmailVerificationRequest;
import com.dogdog.nomat.domain.emailverification.service.EmailVerificationService;
import com.dogdog.nomat.global.dto.ApiResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @Value("${app.auth.jwt.access-token-validity-seconds}")
    private long accessTokenValiditySeconds;

    @Value("${app.auth.jwt.refresh-token-validity-seconds}")
    private long refreshTokenValiditySeconds;

    @Value("${app.auth.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.auth.cookie.same-site:Lax}")
    private String cookieSameSite;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ApiResponse.success("success_register");
    }

    @PostMapping("/email-verifications")
    public ApiResponse<EmailVerificationSentResponse> sendSignupEmailVerification(
            @Valid @RequestBody SendEmailVerificationRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(
                "success_send_email_verification",
                emailVerificationService.sendSignupCode(request.email(), servletRequest.getRemoteAddr())
        );
    }

    @PostMapping("/email-verifications/confirm")
    public ApiResponse<EmailVerificationConfirmedResponse> confirmSignupEmailVerification(
            @Valid @RequestBody ConfirmEmailVerificationRequest request
    ) {
        return ApiResponse.of(
                "success_confirm_email_verification",
                emailVerificationService.confirmSignupCode(request.email(), request.code())
        );
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        LoginResponse loginResponse = authService.login(request);
        addAuthCookies(response, loginResponse.accessToken(), loginResponse.refreshToken());
        return ApiResponse.of("success_login", loginResponse);
    }

    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<GuestLoginResponse> loginGuest(
            @Valid @RequestBody GuestLoginRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        GuestLoginResponse loginResponse = authService.loginGuest(request);
        addAuthCookies(response, loginResponse.accessToken(), loginResponse.refreshToken());
        return ApiResponse.of("success_guest_login", loginResponse);
    }

    @PatchMapping("/guest/nickname")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<GuestLoginResponse> changeGuestNickname(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GuestLoginRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        GuestLoginResponse loginResponse = authService.changeGuestNickname(AuthenticatedUser.from(jwt), request);
        addAuthCookies(response, loginResponse.accessToken(), loginResponse.refreshToken());
        return ApiResponse.of("success_modify_guest_nickname", loginResponse);
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<RefreshResponse> refresh(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshTokenCookie,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        RefreshResponse refreshResponse = StringUtils.hasText(authorizationHeader)
                ? authService.refresh(authorizationHeader)
                : authService.refreshWithToken(refreshTokenCookie);
        addCookie(response, ACCESS_TOKEN_COOKIE_NAME, refreshResponse.accessToken(), accessTokenValiditySeconds);
        return ApiResponse.of("success_refresh", refreshResponse);
    }

    // TODO: refreshToken 저장소 도입 시 로그아웃에서 refreshToken 폐기 처리 구현
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> logout(jakarta.servlet.http.HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE_NAME);
        clearCookie(response, REFRESH_TOKEN_COOKIE_NAME);
        return ApiResponse.success("success_logout");
    }

    private void addAuthCookies(
            jakarta.servlet.http.HttpServletResponse response,
            String accessToken,
            String refreshToken
    ) {
        addCookie(response, ACCESS_TOKEN_COOKIE_NAME, accessToken, accessTokenValiditySeconds);
        addCookie(response, REFRESH_TOKEN_COOKIE_NAME, refreshToken, refreshTokenValiditySeconds);
    }

    private void addCookie(
            jakarta.servlet.http.HttpServletResponse response,
            String name,
            String value,
            long maxAgeSeconds
    ) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build()
                .toString());
    }

    private void clearCookie(jakarta.servlet.http.HttpServletResponse response, String name) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build()
                .toString());
    }
}
