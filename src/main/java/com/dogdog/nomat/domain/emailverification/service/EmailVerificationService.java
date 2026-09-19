package com.dogdog.nomat.domain.emailverification.service;

import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationConfirmedResponse;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationSentResponse;
import com.dogdog.nomat.domain.emailverification.dto.LoginIdRecoveryResponse;
import com.dogdog.nomat.domain.emailverification.dto.PasswordResetConfirmedResponse;
import com.dogdog.nomat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationChallengeManager challengeManager;
    private final EmailVerificationRateLimiter rateLimiter;
    private final EmailVerificationRequestLock requestLock;

    public EmailVerificationSentResponse sendSignupCode(String email, String clientAddress) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.signupChallengeKey(normalizedEmail);
        try {
            EmailVerificationSentResponse response = requestLock.execute(challengeKey, () -> {
                rateLimiter.checkAndRecord(normalizedEmail, clientAddress);
                return challengeManager.issueSignupCode(normalizedEmail);
            });
            log.info("event=email_verification_sent purpose=SIGNUP");
            return response;
        } catch (EmailVerificationMailException exception) {
            logDeliveryFailure("SIGNUP", exception);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_delivery_failed");
        }
    }

    public EmailVerificationSentResponse sendProfileChangeCode(Long userId, String email, String clientAddress) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.profileChangeChallengeKey(userId, normalizedEmail);
        try {
            EmailVerificationSentResponse response = requestLock.execute(challengeKey, () -> {
                rateLimiter.checkAndRecord(normalizedEmail, clientAddress);
                return challengeManager.issueProfileChangeCode(userId, normalizedEmail);
            });
            log.info("event=email_verification_sent purpose=PROFILE_CHANGE userId={}", userId);
            return response;
        } catch (EmailVerificationMailException exception) {
            logDeliveryFailure("PROFILE_CHANGE", exception);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_delivery_failed");
        }
    }

    public EmailVerificationConfirmedResponse confirmSignupCode(String email, String code) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.signupChallengeKey(normalizedEmail);
        EmailVerificationConfirmedResponse response = requestLock.execute(
                challengeKey,
                () -> challengeManager.confirmSignupCode(normalizedEmail, code)
        );
        log.info("event=email_verification_confirmed purpose=SIGNUP");
        return response;
    }

    public EmailVerificationConfirmedResponse confirmProfileChangeCode(Long userId, String email, String code) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.profileChangeChallengeKey(userId, normalizedEmail);
        EmailVerificationConfirmedResponse response = requestLock.execute(
                challengeKey,
                () -> challengeManager.confirmProfileChangeCode(userId, normalizedEmail, code)
        );
        log.info("event=email_verification_confirmed purpose=PROFILE_CHANGE userId={}", userId);
        return response;
    }

    public EmailVerificationSentResponse sendLoginIdRecoveryCode(String email, String clientAddress) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.loginIdRecoveryChallengeKey(normalizedEmail);
        try {
            EmailVerificationSentResponse response = requestLock.execute(challengeKey, () -> {
                rateLimiter.checkAndRecord(normalizedEmail, clientAddress);
                return challengeManager.issueLoginIdRecoveryCode(normalizedEmail);
            });
            log.info("event=email_verification_sent purpose=LOGIN_ID_RECOVERY");
            return response;
        } catch (EmailVerificationMailException exception) {
            logDeliveryFailure("LOGIN_ID_RECOVERY", exception);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_delivery_failed");
        }
    }

    public LoginIdRecoveryResponse confirmLoginIdRecoveryCode(String email, String code) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.loginIdRecoveryChallengeKey(normalizedEmail);
        LoginIdRecoveryResponse response = requestLock.execute(
                challengeKey,
                () -> challengeManager.confirmLoginIdRecoveryCode(normalizedEmail, code)
        );
        log.info("event=email_verification_confirmed purpose=LOGIN_ID_RECOVERY");
        return response;
    }

    public EmailVerificationSentResponse sendPasswordResetCode(String loginId, String email, String clientAddress) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.passwordResetChallengeKey(loginId, normalizedEmail);
        try {
            EmailVerificationSentResponse response = requestLock.execute(challengeKey, () -> {
                rateLimiter.checkAndRecord(normalizedEmail, clientAddress);
                return challengeManager.issuePasswordResetCode(loginId, normalizedEmail);
            });
            log.info("event=email_verification_sent purpose=PASSWORD_RESET");
            return response;
        } catch (EmailVerificationMailException exception) {
            logDeliveryFailure("PASSWORD_RESET", exception);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_delivery_failed");
        }
    }

    public PasswordResetConfirmedResponse confirmPasswordResetCode(String loginId, String email, String code) {
        String normalizedEmail = requireEmail(email);
        String challengeKey = challengeManager.passwordResetChallengeKey(loginId, normalizedEmail);
        PasswordResetConfirmedResponse response = requestLock.execute(
                challengeKey,
                () -> challengeManager.confirmPasswordResetCode(loginId, normalizedEmail, code)
        );
        log.info("event=email_verification_confirmed purpose=PASSWORD_RESET userIdResolved=true");
        return response;
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

    private void logDeliveryFailure(String purpose, EmailVerificationMailException exception) {
        Throwable cause = exception.getCause();
        String errorType = cause == null ? exception.getClass().getSimpleName() : cause.getClass().getSimpleName();
        log.warn("event=email_verification_delivery_failed purpose={} errorType={}", purpose, errorType);
    }
}
