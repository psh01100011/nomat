package com.dogdog.nomat.domain.emailverification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EmailVerificationChallengeTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 9, 19, 12, 0);

    @Test
    void challengeEnforcesExpiryAndMaximumAttempts() {
        EmailVerificationChallenge challenge = challenge();

        assertThat(challenge.canVerify(REQUESTED_AT.plusMinutes(9), 2)).isTrue();

        challenge.recordFailedAttempt();
        challenge.recordFailedAttempt();

        assertThat(challenge.canVerify(REQUESTED_AT.plusMinutes(9), 2)).isFalse();
        assertThat(challenge.canVerify(REQUESTED_AT.plusMinutes(10), 5)).isFalse();
    }

    @Test
    void reissueInvalidatesPreviousCompletionAndResetsAttempts() {
        EmailVerificationChallenge challenge = challenge();
        challenge.recordFailedAttempt();
        challenge.complete("old-token-hash", REQUESTED_AT.plusMinutes(1), Duration.ofMinutes(30));

        challenge.reissue("new-code-hash", REQUESTED_AT.plusMinutes(2), Duration.ofMinutes(10));

        assertThat(challenge.getFailedAttemptCount()).isZero();
        assertThat(challenge.getCompletionTokenHash()).isNull();
        assertThat(challenge.canVerify(REQUESTED_AT.plusMinutes(3), 5)).isTrue();
    }

    @Test
    void completionTokenCanOnlyBeConsumedOnceBeforeExpiry() {
        EmailVerificationChallenge challenge = challenge();
        LocalDateTime completedAt = REQUESTED_AT.plusMinutes(1);
        challenge.complete("token-hash", completedAt, Duration.ofMinutes(30));

        assertThat(challenge.canConsume("wrong-hash", completedAt.plusMinutes(1))).isFalse();
        assertThat(challenge.canConsume("token-hash", completedAt.plusMinutes(29))).isTrue();
        assertThat(challenge.canConsume("token-hash", completedAt.plusMinutes(30))).isFalse();

        challenge.consume(completedAt.plusMinutes(2));

        assertThat(challenge.canConsume("token-hash", completedAt.plusMinutes(3))).isFalse();
    }

    @Test
    void challengeCanRoundTripThroughRedisJson() throws Exception {
        EmailVerificationChallenge challenge = challenge();
        JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

        String json = objectMapper.writeValueAsString(challenge);
        EmailVerificationChallenge restored = objectMapper.readValue(json, EmailVerificationChallenge.class);

        assertThat(restored.getChallengeKey()).isEqualTo(challenge.getChallengeKey());
        assertThat(restored.getExpiresAt()).isEqualTo(challenge.getExpiresAt());
        assertThat(restored.getFailedAttemptCount()).isZero();
    }

    private EmailVerificationChallenge challenge() {
        return EmailVerificationChallenge.create(
                "signup:tester@example.com",
                "tester@example.com",
                EmailVerificationPurpose.SIGNUP,
                null,
                "code-hash",
                REQUESTED_AT,
                Duration.ofMinutes(10)
        );
    }
}
