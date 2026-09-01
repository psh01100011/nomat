package com.dogdog.nomat.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

class SecurityConfigTest {

    private final BearerTokenResolver resolver = new SecurityConfig().bearerTokenResolver();

    @Test
    void bearerTokenResolverUsesAuthorizationHeaderFirst() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");
        request.setCookies(new Cookie("access_token", "cookie-token"));

        assertThat(resolver.resolve(request)).isEqualTo("header-token");
    }

    @Test
    void bearerTokenResolverFallsBackToAccessTokenCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "cookie-token"));

        assertThat(resolver.resolve(request)).isEqualTo("cookie-token");
    }

    @Test
    void bearerTokenResolverIgnoresRefreshEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/auth/refresh");
        request.setCookies(new Cookie("access_token", "cookie-token"));

        assertThat(resolver.resolve(request)).isNull();
    }
}
