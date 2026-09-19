package com.dogdog.nomat.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.auth.dto.LoginRequest;
import com.dogdog.nomat.domain.auth.dto.LoginResponse;
import com.dogdog.nomat.domain.auth.dto.RefreshResponse;
import com.dogdog.nomat.domain.auth.service.AuthService;
import com.dogdog.nomat.domain.emailverification.service.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private EmailVerificationService emailVerificationService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService, emailVerificationService);
        ReflectionTestUtils.setField(authController, "accessTokenValiditySeconds", 1800L);
        ReflectionTestUtils.setField(authController, "refreshTokenValiditySeconds", 1209600L);
        ReflectionTestUtils.setField(authController, "cookieSecure", false);
        ReflectionTestUtils.setField(authController, "cookieSameSite", "Lax");
    }

    @Test
    void loginAddsHttpOnlyAuthCookies() {
        LoginRequest request = new LoginRequest("testuser", "password123!");
        given(authService.login(request)).willReturn(new LoginResponse(1L, "access-token", "refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.login(request, response);

        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("access_token=access-token")
                        .contains("HttpOnly")
                        .contains("SameSite=Lax"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("refresh_token=refresh-token")
                        .contains("HttpOnly")
                        .contains("SameSite=Lax"));
    }

    @Test
    void refreshUsesRefreshCookieWhenAuthorizationHeaderIsMissing() {
        given(authService.refreshWithToken("refresh-token")).willReturn(new RefreshResponse("new-access-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.refresh(null, "refresh-token", response);

        verify(authService).refreshWithToken("refresh-token");
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("access_token=new-access-token")
                        .contains("HttpOnly"));
    }

    @Test
    void refreshPrefersAuthorizationHeader() {
        given(authService.refresh("Bearer refresh-token")).willReturn(new RefreshResponse("new-access-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.refresh("Bearer refresh-token", "cookie-refresh-token", response);

        verify(authService).refresh("Bearer refresh-token");
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
}
