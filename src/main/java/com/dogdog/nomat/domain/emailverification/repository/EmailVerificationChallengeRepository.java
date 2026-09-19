package com.dogdog.nomat.domain.emailverification.repository;

import com.dogdog.nomat.domain.emailverification.entity.EmailVerificationChallenge;
import com.dogdog.nomat.domain.emailverification.service.EmailVerificationSecretGenerator;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class EmailVerificationChallengeRepository {

    private static final String KEY_PREFIX = "email-verification:challenges:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmailVerificationSecretGenerator secretGenerator;

    public Optional<EmailVerificationChallenge> findByChallengeKey(String challengeKey) {
        String value = redisTemplate.opsForValue().get(redisKey(challengeKey));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(value));
    }

    public void save(EmailVerificationChallenge challenge, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            redisTemplate.delete(redisKey(challenge.getChallengeKey()));
            return;
        }
        redisTemplate.opsForValue().set(redisKey(challenge.getChallengeKey()), serialize(challenge), ttl);
    }

    private String redisKey(String challengeKey) {
        return KEY_PREFIX + secretGenerator.hashToken(challengeKey);
    }

    private String serialize(EmailVerificationChallenge challenge) {
        try {
            return objectMapper.writeValueAsString(challenge);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize email verification challenge.", exception);
        }
    }

    private EmailVerificationChallenge deserialize(String value) {
        try {
            return objectMapper.readValue(value, EmailVerificationChallenge.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize email verification challenge.", exception);
        }
    }
}
