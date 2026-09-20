package com.dogdog.nomat.domain.emailverification.service;

import com.dogdog.nomat.domain.emailverification.config.EmailVerificationProperties;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationRateLimiter {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final EmailVerificationProperties properties;
    private final EmailVerificationSecretGenerator secretGenerator;

    public void checkAndRecord(String email, String clientAddress) {
        checkAndIncrement(
                "email-verification:rate:email:" + secretGenerator.hashToken(email),
                properties.getMaxSendsPerEmailHour(),
                "email"
        );
        checkAndIncrement(
                "email-verification:rate:ip:" + secretGenerator.hashToken(normalizeAddress(clientAddress)),
                properties.getMaxSendsPerIpHour(),
                "ip"
        );
    }

    private void checkAndIncrement(String key, int limit, String scope) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_LIMIT_WINDOW);
        }
        if (count != null && count > limit) {
            Long remainingSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfterSeconds = remainingSeconds == null || remainingSeconds < 0
                    ? RATE_LIMIT_WINDOW.toSeconds()
                    : remainingSeconds;
            log.warn(
                    "event=email_verification_rate_limited scope={} limit={} retryAfterSeconds={}",
                    scope,
                    limit,
                    retryAfterSeconds
            );
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "email_verification_rate_limited",
                    Map.of("retryAfterSeconds", retryAfterSeconds)
            );
        }
    }

    private String normalizeAddress(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
    }
}
