package com.dogdog.nomat.domain.emailverification.service;

import com.dogdog.nomat.global.exception.BusinessException;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationRequestLock {

    private static final Duration LOCK_TTL = Duration.ofSeconds(15);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final EmailVerificationSecretGenerator secretGenerator;

    public <T> T execute(String challengeKey, Supplier<T> action) {
        String lockKey = "email-verification:lock:" + secretGenerator.hashToken(challengeKey);
        String ownerToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, ownerToken, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(HttpStatus.CONFLICT, "email_verification_request_in_progress");
        }

        try {
            return action.get();
        } finally {
            redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(lockKey), ownerToken);
        }
    }
}
