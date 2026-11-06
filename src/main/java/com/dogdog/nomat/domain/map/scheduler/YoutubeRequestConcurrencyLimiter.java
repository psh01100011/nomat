package com.dogdog.nomat.domain.map.scheduler;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.audio-processing", name = "enabled", havingValue = "true")
public class YoutubeRequestConcurrencyLimiter {

    private static final String PERMIT_KEY = "audio-processing:youtube-permits";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local now = redis.call('TIME')
            local nowMillis = (now[1] * 1000) + math.floor(now[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', nowMillis)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[1]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], nowMillis + tonumber(ARGV[2]), ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('ZSCORE', KEYS[1], ARGV[2]) == false then
                return 0
            end
            local now = redis.call('TIME')
            local nowMillis = (now[1] * 1000) + math.floor(now[2] / 1000)
            redis.call('ZADD', KEYS[1], 'XX', nowMillis + tonumber(ARGV[1]), ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local removed = redis.call('ZREM', KEYS[1], ARGV[1])
            if redis.call('ZCARD', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[1])
            end
            return removed
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AudioProcessingProperties properties;
    private final Map<String, RedisPermit> activePermits = new ConcurrentHashMap<>();

    public YoutubeRequestConcurrencyLimiter(
            StringRedisTemplate redisTemplate,
            AudioProcessingProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Optional<Permit> tryAcquire() {
        String token = UUID.randomUUID().toString();
        Long acquired = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                Collections.singletonList(PERMIT_KEY),
                String.valueOf(properties.getYoutubeMaxConcurrency()),
                String.valueOf(leaseMillis()),
                token
        );
        if (!Long.valueOf(1L).equals(acquired)) {
            return Optional.empty();
        }

        RedisPermit permit = new RedisPermit(token);
        activePermits.put(token, permit);
        return Optional.of(permit);
    }

    @Scheduled(fixedDelayString = "${app.audio-processing.youtube-permit-renewal-ms:30000}")
    public void renewActivePermits() {
        activePermits.forEach((token, permit) -> {
            if (permit.closed.get()) {
                return;
            }
            try {
                Long renewed = redisTemplate.execute(
                        RENEW_SCRIPT,
                        Collections.singletonList(PERMIT_KEY),
                        String.valueOf(leaseMillis()),
                        token
                );
                if (!Long.valueOf(1L).equals(renewed)) {
                    activePermits.remove(token, permit);
                    log.error("event=youtube_permit_lost token={}", token);
                }
            } catch (RuntimeException exception) {
                log.warn("event=youtube_permit_renewal_failed token={}", token, exception);
            }
        });
    }

    private long leaseMillis() {
        return properties.getYoutubePermitLeaseSeconds() * 1000;
    }

    public interface Permit extends AutoCloseable {

        @Override
        void close();
    }

    private final class RedisPermit implements Permit {

        private final String token;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RedisPermit(String token) {
            this.token = token;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            activePermits.remove(token, this);
            try {
                redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(PERMIT_KEY), token);
            } catch (RuntimeException exception) {
                log.warn("event=youtube_permit_release_failed token={}", token, exception);
            }
        }
    }
}
