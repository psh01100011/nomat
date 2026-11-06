package com.dogdog.nomat.domain.room.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomMutationLock {

    private static final String LOCK_PREFIX = "rooms:mutation-lock:";
    private static final Duration LOCK_DURATION = Duration.ofSeconds(30);
    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(35);
    private static final Duration RETRY_DELAY = Duration.ofMillis(25);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void execute(Long roomId, Runnable action) {
        String lockKey = LOCK_PREFIX + roomId;
        String ownerToken = UUID.randomUUID().toString();
        Instant deadline = Instant.now().plus(ACQUIRE_TIMEOUT);

        while (!tryAcquire(lockKey, ownerToken)) {
            if (!Instant.now().isBefore(deadline)) {
                throw new IllegalStateException("Timed out while waiting for room mutation lock: " + roomId);
            }
            sleepBeforeRetry();
        }

        try {
            action.run();
        } finally {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey), ownerToken);
        }
    }

    private boolean tryAcquire(String lockKey, String ownerToken) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, ownerToken, LOCK_DURATION));
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for room mutation lock.", exception);
        }
    }
}
