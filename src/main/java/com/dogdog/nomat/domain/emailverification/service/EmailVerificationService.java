package com.dogdog.nomat.domain.emailverification.service;

import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationConfirmedResponse;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationSentResponse;
import com.dogdog.nomat.domain.emailverification.dto.LoginIdRecoveryResponse;
import com.dogdog.nomat.domain.emailverification.dto.PasswordResetConfirmedResponse;
import com.dogdog.nomat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationChallengeManager challengeManager;
    private final EmailVerificationRateLimiter rateLimiter;
    private final EmailVerificationRequestLock requestLock;

    public EmailVerificationSentResponse sendSignupCode(String email, String clientAddress) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.signupChallengeKey(normalizedEmail);
        try {
            return requestLock.execute(challengeKey, () -> {
                rateLimiter.checkAndRecord(normalizedEmail, clientAddress);
                return challengeManager.issueSignupCode(normalizedEmail);
            });
        } catch (EmailVerificationMailException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_delivery_failed");
        }
    }

    public EmailVerificationSentResponse sendProfileChangeCode(Long userId, String email, String clientAddress) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.profileChangeChallengeKey(userId, normalizedEmail);
        try {
            return requestLock.execute(challengeKey, () -> {
                rateLimiter.checkAndRecord(normalizedEmail, clientAddress);
                return challengeManager.issueProfileChangeCode(userId, normalizedEmail);
            });
        } catch (EmailVerificationMailException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_delivery_failed");
        }
    }

    public EmailVerificationConfirmedResponse confirmSignupCode(String email, String code) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.signupChallengeKey(normalizedEmail);
        return requestLock.execute(challengeKey, () -> challengeManager.confirmSignupCode(normalizedEmail, code));
    }

    public EmailVerificationConfirmedResponse confirmProfileChangeCode(Long userId, String email, String code) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.profileChangeChallengeKey(userId, normalizedEmail);
        return requestLock.execute(
                challengeKey,
                () -> challengeManager.confirmProfileChangeCode(userId, normalizedEmail, code)
        );
    }

    public EmailVerificationSentResponse sendLoginIdRecoveryCode(String email, String clientAddress) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.loginIdRecoveryChallengeKey(normalizedEmail);
        try {
            return requestLock.execute(challengeKey, () -> {
                rateLimiter.checkAndRecord(normalizedEmail, clientAddress);
                return challengeManager.issueLoginIdRecoveryCode(normalizedEmail);
            });
        } catch (EmailVerificationMailException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_delivery_failed");
        }
    }

    public LoginIdRecoveryResponse confirmLoginIdRecoveryCode(String email, String code) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.loginIdRecoveryChallengeKey(normalizedEmail);
        return requestLock.execute(
                challengeKey,
                () -> challengeManager.confirmLoginIdRecoveryCode(normalizedEmail, code)
        );
    }

    public EmailVerificationSentResponse sendPasswordResetCode(String loginId, String email, String clientAddress) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.passwordResetChallengeKey(loginId, normalizedEmail);
        try {
            return requestLock.execute(challengeKey, () -> {
                rateLimiter.checkAndRecord(normalizedEmail, clientAddress);
                return challengeManager.issuePasswordResetCode(loginId, normalizedEmail);
            });
        } catch (EmailVerificationMailException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_delivery_failed");
        }
    }

    public PasswordResetConfirmedResponse confirmPasswordResetCode(String loginId, String email, String code) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.passwordResetChallengeKey(loginId, normalizedEmail);
        return requestLock.execute(
                challengeKey,
                () -> challengeManager.confirmPasswordResetCode(loginId, normalizedEmail, code)
        );
    }

    public Long consumePasswordResetToken(String loginId, String email, String completionToken) {
        requireCompletionToken(completionToken);
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.passwordResetChallengeKey(loginId, normalizedEmail);
        return requestLock.execute(
                challengeKey,
                () -> challengeManager.consumePasswordResetToken(loginId, normalizedEmail, completionToken)
        );
    }

    public void consumeSignupToken(String email, String completionToken) {
        requireCompletionToken(completionToken);
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.signupChallengeKey(normalizedEmail);
        requestLock.execute(challengeKey, () -> {
            challengeManager.consumeSignupToken(normalizedEmail, completionToken);
            return null;
        });
    }

    public void consumeProfileChangeToken(Long userId, String email, String completionToken) {
        requireCompletionToken(completionToken);
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.profileChangeChallengeKey(userId, normalizedEmail);
        requestLock.execute(challengeKey, () -> {
            challengeManager.consumeProfileChangeToken(userId, normalizedEmail, completionToken);
            return null;
        });
    }

    private String requireEmail(String email) {
        String normalizedEmail = EmailAddressNormalizer.normalizeNullable(email);
        if (!EmailAddressNormalizer.isValid(normalizedEmail)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_email");
        }
        return normalizedEmail;
    }

    private void requireCompletionToken(String completionToken) {
        if (completionToken == null || completionToken.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "email_verification_required");
        }
    }
}
