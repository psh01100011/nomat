package com.dogdog.nomat.domain.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AssetCleanupCursorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AssetCleanupCursor cursor;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        cursor = new AssetCleanupCursor(redisTemplate);
    }

    @Test
    void readAndSaveUseSharedRedisCursor() {
        given(valueOperations.get("asset-cleanup:attached-scan-cursor")).willReturn("500");

        assertThat(cursor.read()).isEqualTo(500L);

        cursor.save(750L);
        verify(valueOperations).set("asset-cleanup:attached-scan-cursor", "750");
    }

    @Test
    void readReturnsBeginningForMissingOrInvalidCursor() {
        given(valueOperations.get("asset-cleanup:attached-scan-cursor"))
                .willReturn(null, "invalid");

        assertThat(cursor.read()).isZero();
        assertThat(cursor.read()).isZero();
    }
}
