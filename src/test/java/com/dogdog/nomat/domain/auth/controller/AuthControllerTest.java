package com.dogdog.nomat.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.auth.dto.GuestLoginRequest;
import com.dogdog.nomat.domain.auth.dto.GuestLoginResponse;
import com.dogdog.nomat.domain.auth.dto.LoginRequest;
import com.dogdog.nomat.domain.auth.dto.LoginResponse;
import com.dogdog.nomat.domain.auth.dto.RefreshResponse;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUser;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUserType;
import com.dogdog.nomat.domain.auth.model.IssuedAuthSession;
import com.dogdog.nomat.domain.auth.service.AuthService;
import com.dogdog.nomat.domain.emailverification.service.EmailVerificationService;
import com.dogdog.nomat.global.dto.ApiResponse;
import com.dogdog.nomat.global.http.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private ClientIpResolver clientIpResolver;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService, emailVerificationService, clientIpResolver);
        ReflectionTestUtils.setField(authController, "accessTokenValiditySeconds", 1800L);
        ReflectionTestUtils.setField(authController, "refreshTokenValiditySeconds", 1209600L);
        ReflectionTestUtils.setField(authController, "cookieSecure", false);
        ReflectionTestUtils.setField(authController, "cookieSameSite", "Lax");
    }

    @Test
    void loginAddsHttpOnlyAuthCookies() {
        LoginRequest request = new LoginRequest("testuser", "password123!");
        given(authService.login(request)).willReturn(new IssuedAuthSession<>(
                new LoginResponse(1L, "access-token"),
                "refresh-token"
        ));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<LoginResponse> apiResponse = authController.login(request, response);

        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("access_token=access-token")
                        .contains("HttpOnly")
                        .contains("SameSite=Lax"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("refresh_token=refresh-token")
                        .contains("HttpOnly")
                        .contains("SameSite=Lax"));
        assertThat(JsonMapper.builder().build().writeValueAsString(apiResponse))
                .contains("accessToken")
                .doesNotContain("refreshToken", "refresh-token");
    }

    @Test
    void guestLoginKeepsRefreshTokenOutOfResponseBody() {
        GuestLoginRequest request = new GuestLoginRequest("guest");
        given(authService.loginGuest(request)).willReturn(new IssuedAuthSession<>(
                new GuestLoginResponse(-1L, "GUEST", "guest", "access-token"),
                "refresh-token"
        ));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<GuestLoginResponse> apiResponse = authController.loginGuest(request, response);

        assertThat(JsonMapper.builder().build().writeValueAsString(apiResponse))
                .contains("accessToken")
                .doesNotContain("refreshToken", "refresh-token");
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("refresh_token=refresh-token")
                        .contains("HttpOnly"));
    }

    @Test
    void guestNicknameChangeKeepsRotatedRefreshTokenInCookieOnly() {
        GuestLoginRequest request = new GuestLoginRequest("new-guest");
        Jwt jwt = guestJwt(-1L, "guest");
        given(authService.changeGuestNickname(
                new AuthenticatedUser(-1L, AuthenticatedUserType.GUEST, "guest"),
                request
        )).willReturn(new IssuedAuthSession<>(
                new GuestLoginResponse(-1L, "GUEST", "new-guest", "new-access-token"),
                "new-refresh-token"
        ));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<GuestLoginResponse> apiResponse = authController.changeGuestNickname(jwt, request, response);

        assertThat(JsonMapper.builder().build().writeValueAsString(apiResponse))
                .contains("new-access-token")
                .doesNotContain("refreshToken", "new-refresh-token");
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("refresh_token=new-refresh-token")
                        .contains("HttpOnly"));
    }

    @Test
    void refreshUsesRefreshCookieWhenAuthorizationHeaderIsMissing() {
        given(authService.refreshWithToken("refresh-token")).willReturn(new RefreshResponse("new-access-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.refresh("refresh-token", response);

        verify(authService).refreshWithToken("refresh-token");
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("access_token=new-access-token")
                        .contains("HttpOnly"));
    }

    @Test
    void logoutClearsAuthCookies() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.logout(response);

        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("access_token=")
                        .contains("Max-Age=0"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("refresh_token=")
                        .contains("Max-Age=0"));
    }

    private Jwt guestJwt(Long userId, String nickname) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject("guest:" + userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("userId", userId)
                .claim("userType", "GUEST")
                .claim("nickname", nickname)
                .claim("tokenType", "access")
                .build();
    }
}
