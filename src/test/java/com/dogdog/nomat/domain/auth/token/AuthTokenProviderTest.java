package com.dogdog.nomat.domain.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class AuthTokenProviderTest {

    private static final long MAX_JAVASCRIPT_SAFE_INTEGER = (1L << 53) - 1;

    @Test
    void generateGuestUserIdStaysWithinJavaScriptSafeIntegerRange() {
        AuthTokenProvider provider = new AuthTokenProvider(
                org.mockito.Mockito.mock(JwtEncoder.class),
                org.mockito.Mockito.mock(JwtDecoder.class),
                org.mockito.Mockito.mock(JwtDecoder.class)
        );

        for (int attempt = 0; attempt < 100; attempt++) {
            assertThat(provider.generateGuestUserId())
                    .isBetween(-MAX_JAVASCRIPT_SAFE_INTEGER, -1L);
        }
    }
}
