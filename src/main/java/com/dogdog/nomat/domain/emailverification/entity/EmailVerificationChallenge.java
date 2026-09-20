package com.dogdog.nomat.domain.emailverification.entity;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationChallenge {

    private String challengeKey;
    private String email;
    private EmailVerificationPurpose purpose;
    private Long userId;
    private String codeHash;
    private LocalDateTime expiresAt;
    private int failedAttemptCount;
    private LocalDateTime lastSentAt;
    private String completionTokenHash;
    private LocalDateTime completionTokenExpiresAt;
    private LocalDateTime completedAt;
    private LocalDateTime consumedAt;

    private EmailVerificationChallenge(
            String challengeKey,
            String email,
            EmailVerificationPurpose purpose,
            Long userId,
            String codeHash,
            LocalDateTime requestedAt,
            Duration codeValidity
    ) {
        this.challengeKey = challengeKey;
        this.email = email;
        this.purpose = purpose;
        this.userId = userId;
        issue(codeHash, requestedAt, codeValidity);
    }

    public static EmailVerificationChallenge create(
            String challengeKey,
            String email,
            EmailVerificationPurpose purpose,
            Long userId,
            String codeHash,
            LocalDateTime requestedAt,
            Duration codeValidity
    ) {
        return new EmailVerificationChallenge(
                challengeKey,
                email,
                purpose,
                userId,
                codeHash,
                requestedAt,
                codeValidity
        );
    }

    public void reissue(String codeHash, LocalDateTime requestedAt, Duration codeValidity) {
        issue(codeHash, requestedAt, codeValidity);
    }

    public boolean canVerify(LocalDateTime now, int maxAttempts) {
        return completedAt == null
                && consumedAt == null
                && now.isBefore(expiresAt)
                && failedAttemptCount < maxAttempts;
    }

    public void recordFailedAttempt() {
        this.failedAttemptCount++;
    }

    public void complete(String completionTokenHash, LocalDateTime completedAt, Duration tokenValidity) {
        this.completionTokenHash = completionTokenHash;
        this.completionTokenExpiresAt = completedAt.plus(tokenValidity);
        this.completedAt = completedAt;
    }

    public boolean canConsume(String tokenHash, LocalDateTime now) {
        return completedAt != null
                && consumedAt == null
                && completionTokenHash != null
                && completionTokenHash.equals(tokenHash)
                && now.isBefore(completionTokenExpiresAt);
    }

    public void consume(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    private void issue(String codeHash, LocalDateTime requestedAt, Duration codeValidity) {
        this.codeHash = codeHash;
        this.expiresAt = requestedAt.plus(codeValidity);
        this.failedAttemptCount = 0;
        this.lastSentAt = requestedAt;
        this.completionTokenHash = null;
        this.completionTokenExpiresAt = null;
        this.completedAt = null;
        this.consumedAt = null;
    }

}
