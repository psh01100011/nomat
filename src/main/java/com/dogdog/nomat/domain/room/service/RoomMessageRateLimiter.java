package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.global.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomMessageRateLimiter {

    private static final String KEY_PREFIX = "rooms:message-rate:";
    private static final long SCALE = 1_000L;
    private static final long CAPACITY_TOKENS = 6L * SCALE;
    private static final long REFILL_TOKENS_PER_SECOND = 3L * SCALE;
    private static final long MESSAGE_COST_TOKENS = 1L * SCALE;
    private static final long RETRY_AFTER_TARGET_TOKENS = 3L * SCALE;
    private static final long MIN_RETRY_AFTER_MILLIS = 1_000L;
    private static final long MAX_RETRY_AFTER_MILLIS = 2_000L;
    private static final Duration BUCKET_TTL = Duration.ofSeconds(10);
    private static final DefaultRedisScript<Long> CONSUME_TOKEN_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local cost = tonumber(ARGV[4])
            local target = tonumber(ARGV[5])
            local min_retry_millis = tonumber(ARGV[6])
            local max_retry_millis = tonumber(ARGV[7])
            local ttl_millis = tonumber(ARGV[8])

            local bucket = redis.call('HMGET', key, 'tokens', 'updatedAt')
            local tokens = tonumber(bucket[1]) or capacity
            local updated_at = tonumber(bucket[2]) or now
            local elapsed = now - updated_at
            if elapsed < 0 then
                elapsed = 0
            end

            tokens = math.min(capacity, tokens + math.floor(elapsed * refill / 1000))
            if tokens >= cost then
                tokens = tokens - cost
                redis.call('HSET', key, 'tokens', tokens, 'updatedAt', now)
                redis.call('PEXPIRE', key, ttl_millis)
                return 0
            end

            local missing = target - tokens
            if missing < cost - tokens then
                missing = cost - tokens
            end
            local retry_millis = math.ceil(missing * 1000 / refill)
            retry_millis = math.max(min_retry_millis, math.min(max_retry_millis, retry_millis))
            redis.call('HSET', key, 'tokens', tokens, 'updatedAt', now)
            redis.call('PEXPIRE', key, ttl_millis)
            return math.ceil(retry_millis / 1000)
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void checkAllowed(Long roomId, Long userId, String clientMessageId) {
        Long retryAfterSeconds = redisTemplate.execute(
                CONSUME_TOKEN_SCRIPT,
                List.of(rateLimitKey(roomId, userId)),
                String.valueOf(CAPACITY_TOKENS),
                String.valueOf(REFILL_TOKENS_PER_SECOND),
                String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(MESSAGE_COST_TOKENS),
                String.valueOf(RETRY_AFTER_TARGET_TOKENS),
                String.valueOf(MIN_RETRY_AFTER_MILLIS),
                String.valueOf(MAX_RETRY_AFTER_MILLIS),
                String.valueOf(BUCKET_TTL.toMillis())
        );

        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            log.warn(
                    "event=room_message_rate_limited roomId={} userId={} retryAfterSeconds={}",
                    roomId, userId, retryAfterSeconds
            );
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "message_rate_limited",
                    new RateLimitData(retryAfterSeconds.intValue(), clientMessageId)
            );
        }
    }

    private String rateLimitKey(Long roomId, Long userId) {
        return KEY_PREFIX + roomId + ":" + userId;
    }

    public record RateLimitData(
            int retryAfterSeconds,
            String clientMessageId
    ) {
    }
}
