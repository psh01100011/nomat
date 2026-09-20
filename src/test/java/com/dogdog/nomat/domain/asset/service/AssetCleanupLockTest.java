package com.dogdog.nomat.domain.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.asset.config.AssetCleanupProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class AssetCleanupLockTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Runnable action;

    private AssetCleanupLock cleanupLock;

    @BeforeEach
    void setUp() {
        AssetCleanupProperties properties = new AssetCleanupProperties();
        properties.setLockTtlMinutes(30);
        cleanupLock = new AssetCleanupLock(redisTemplate, properties);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    void executeIfAcquiredRunsActionAndReleasesOwnedLock() {
        given(valueOperations.setIfAbsent(
                eq("asset-cleanup:scheduler-lock"),
                anyString(),
                eq(Duration.ofMinutes(30))
        )).willReturn(true);

        boolean acquired = cleanupLock.executeIfAcquired(action);

        assertThat(acquired).isTrue();
        verify(action).run();
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("asset-cleanup:scheduler-lock")),
                anyString()
        );
    }

    @Test
    void executeIfAcquiredSkipsActionWhenAnotherServerOwnsLock() {
        given(valueOperations.setIfAbsent(
                eq("asset-cleanup:scheduler-lock"),
                anyString(),
                eq(Duration.ofMinutes(30))
        )).willReturn(false);

        boolean acquired = cleanupLock.executeIfAcquired(action);

        assertThat(acquired).isFalse();
        verify(action, never()).run();
        verify(redisTemplate, never()).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("asset-cleanup:scheduler-lock")),
                anyString()
        );
    }
}
