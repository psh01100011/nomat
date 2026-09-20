package com.dogdog.nomat.domain.asset.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssetCleanupCursor {

    private static final String CURSOR_KEY = "asset-cleanup:attached-scan-cursor";

    private final StringRedisTemplate redisTemplate;

    public long read() {
        String value = redisTemplate.opsForValue().get(CURSOR_KEY);
        if (value == null) {
            return 0L;
        }

        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public void save(long assetId) {
        redisTemplate.opsForValue().set(CURSOR_KEY, String.valueOf(Math.max(0L, assetId)));
    }
}
