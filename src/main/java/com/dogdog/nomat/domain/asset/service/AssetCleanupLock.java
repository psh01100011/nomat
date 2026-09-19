package com.dogdog.nomat.domain.asset.service;

import com.dogdog.nomat.domain.asset.config.AssetCleanupProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssetCleanupLock {

    private static final String LOCK_KEY = "asset-cleanup:scheduler-lock";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final AssetCleanupProperties cleanupProperties;

    public boolean executeIfAcquired(Runnable action) {
        String ownerToken = UUID.randomUUID().toString();
        Duration ttl = Duration.ofMinutes(cleanupProperties.getLockTtlMinutes());
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, ownerToken, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }

        try {
            action.run();
            return true;
        } finally {
            redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(LOCK_KEY), ownerToken);
        }
    }
}
