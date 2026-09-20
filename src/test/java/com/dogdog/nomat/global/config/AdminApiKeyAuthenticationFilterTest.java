package com.dogdog.nomat.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminApiKeyAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesProtectedRequestWithConfiguredAdminKey() throws Exception {
        AdminApiKeyProperties properties = new AdminApiKeyProperties();
        properties.setApiKey("operations-secret");
        AdminApiKeyAuthenticationFilter filter = new AdminApiKeyAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/audio-processing/jobs");
        request.setServletPath("/admin/audio-processing/jobs");
        request.addHeader("X-Admin-Key", "operations-secret");
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        FilterChain chain = (ignoredRequest, ignoredResponse) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(authentication.get()).isNotNull();
        assertThat(authentication.get().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void doesNotAuthenticateWhenAdminKeyIsMissingOrWrong() throws Exception {
        AdminApiKeyProperties properties = new AdminApiKeyProperties();
        properties.setApiKey("operations-secret");
        AdminApiKeyAuthenticationFilter filter = new AdminApiKeyAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/audio-processing/jobs");
        request.setServletPath("/admin/audio-processing/jobs");
        request.addHeader("X-Admin-Key", "wrong-secret");

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
