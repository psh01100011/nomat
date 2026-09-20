package com.dogdog.nomat.domain.emailverification.service;

import com.dogdog.nomat.domain.emailverification.config.EmailVerificationProperties;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationConfirmedResponse;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationSentResponse;
import com.dogdog.nomat.domain.emailverification.dto.LoginIdRecoveryResponse;
import com.dogdog.nomat.domain.emailverification.dto.PasswordResetConfirmedResponse;
import com.dogdog.nomat.domain.emailverification.entity.EmailVerificationChallenge;
import com.dogdog.nomat.domain.emailverification.entity.EmailVerificationPurpose;
import com.dogdog.nomat.domain.emailverification.repository.EmailVerificationChallengeRepository;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationChallengeManager {

    private final EmailVerificationChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final EmailVerificationMailSender mailSender;
    private final EmailVerificationSecretGenerator secretGenerator;
    private final EmailVerificationProperties properties;
    private final PasswordEncoder passwordEncoder;

    public EmailVerificationSentResponse issueSignupCode(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_email");
        }

        LocalDateTime now = LocalDateTime.now();
        Duration codeValidity = Duration.ofMinutes(properties.getCodeValidityMinutes());
        String challengeKey = signupChallengeKey(email);
        EmailVerificationChallenge challenge = challengeRepository.findByChallengeKey(challengeKey)
                .orElse(null);

        String code = secretGenerator.generateCode();
        String codeHash = passwordEncoder.encode(code);
        if (challenge == null) {
            challenge = EmailVerificationChallenge.create(
                    challengeKey,
                    email,
                    EmailVerificationPurpose.SIGNUP,
                    null,
                    codeHash,
                    now,
                    codeValidity
            );
        } else {
            challenge.reissue(codeHash, now, codeValidity);
        }

        mailSender.sendVerificationCode(email, code);
        challengeRepository.save(challenge, codeValidity);
        return new EmailVerificationSentResponse(codeValidity.toSeconds(), 0);
    }

    public EmailVerificationSentResponse issueProfileChangeCode(Long userId, String email) {
        User user = userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token"));
        if (email.equals(user.getEmail()) && user.hasVerifiedEmail()) {
            throw new BusinessException(HttpStatus.CONFLICT, "email_already_verified");
        }
        if (userRepository.existsByEmailAndIdNot(email, userId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_email");
        }

        LocalDateTime now = LocalDateTime.now();
        Duration codeValidity = Duration.ofMinutes(properties.getCodeValidityMinutes());
        String challengeKey = profileChangeChallengeKey(userId, email);
        EmailVerificationChallenge challenge = challengeRepository.findByChallengeKey(challengeKey)
                .orElse(null);

        String code = secretGenerator.generateCode();
        String codeHash = passwordEncoder.encode(code);
        if (challenge == null) {
            challenge = EmailVerificationChallenge.create(
                    challengeKey,
                    email,
                    EmailVerificationPurpose.PROFILE_CHANGE,
                    userId,
                    codeHash,
                    now,
                    codeValidity
            );
        } else {
            challenge.reissue(codeHash, now, codeValidity);
        }

        mailSender.sendVerificationCode(email, code);
        challengeRepository.save(challenge, codeValidity);
        return new EmailVerificationSentResponse(codeValidity.toSeconds(), 0);
    }

    public EmailVerificationSentResponse issueLoginIdRecoveryCode(String email) {
        User recoveryUser = userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(User::hasVerifiedEmail)
                .orElse(null);
        LocalDateTime now = LocalDateTime.now();
        Duration codeValidity = Duration.ofMinutes(properties.getCodeValidityMinutes());
        String challengeKey = loginIdRecoveryChallengeKey(email);
        EmailVerificationChallenge challenge = challengeRepository.findByChallengeKey(challengeKey)
                .orElse(null);
        String code = secretGenerator.generateCode();
        String codeHash = passwordEncoder.encode(code);

        Long recoveryUserId = recoveryUser == null ? null : recoveryUser.getId();
        if (challenge == null || !Objects.equals(challenge.getUserId(), recoveryUserId)) {
            challenge = EmailVerificationChallenge.create(
                    challengeKey,
                    email,
                    EmailVerificationPurpose.LOGIN_ID_RECOVERY,
                    recoveryUserId,
                    codeHash,
                    now,
                    codeValidity
            );
        } else {
            challenge.reissue(codeHash, now, codeValidity);
        }

        mailSender.sendVerificationCode(email, code);
        challengeRepository.save(challenge, codeValidity);
        return new EmailVerificationSentResponse(codeValidity.toSeconds(), 0);
    }

    public LoginIdRecoveryResponse confirmLoginIdRecoveryCode(String email, String code) {
        EmailVerificationChallenge challenge = challengeRepository
                .findByChallengeKey(loginIdRecoveryChallengeKey(email))
                .orElseThrow(() -> invalidVerificationCode());
        LocalDateTime now = LocalDateTime.now();
        verifyAndConsumeCode(challenge, code, now);

        User user = challenge.getUserId() == null
                ? null
                : userRepository.findById(challenge.getUserId())
                        .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                        .filter(User::hasVerifiedEmail)
                        .filter(foundUser -> Objects.equals(foundUser.getEmail(), email))
                        .orElse(null);
        if (user == null) {
            throw recoverableAccountNotFound();
        }
        return new LoginIdRecoveryResponse(user.getLoginId());
    }

    public EmailVerificationSentResponse issuePasswordResetCode(String loginId, String email) {
        User recoveryUser = findPasswordResetUser(loginId, email);
        LocalDateTime now = LocalDateTime.now();
        Duration codeValidity = Duration.ofMinutes(properties.getCodeValidityMinutes());
        String challengeKey = passwordResetChallengeKey(loginId, email);
        EmailVerificationChallenge challenge = challengeRepository.findByChallengeKey(challengeKey)
                .orElse(null);
        String code = secretGenerator.generateCode();
        String codeHash = passwordEncoder.encode(code);
        Long recoveryUserId = recoveryUser == null ? null : recoveryUser.getId();

        if (challenge == null || !Objects.equals(challenge.getUserId(), recoveryUserId)) {
            challenge = EmailVerificationChallenge.create(
                    challengeKey,
                    email,
                    EmailVerificationPurpose.PASSWORD_RESET,
                    recoveryUserId,
                    codeHash,
                    now,
                    codeValidity
            );
        } else {
            challenge.reissue(codeHash, now, codeValidity);
        }

        mailSender.sendVerificationCode(email, code);
        challengeRepository.save(challenge, codeValidity);
        return new EmailVerificationSentResponse(codeValidity.toSeconds(), 0);
    }

    public PasswordResetConfirmedResponse confirmPasswordResetCode(String loginId, String email, String code) {
        EmailVerificationChallenge challenge = challengeRepository
                .findByChallengeKey(passwordResetChallengeKey(loginId, email))
                .orElseThrow(() -> invalidVerificationCode());
        LocalDateTime now = LocalDateTime.now();
        verifyCode(challenge, code, now);
        User recoveryUser = findPasswordResetUser(loginId, email);
        if (recoveryUser == null || !Objects.equals(challenge.getUserId(), recoveryUser.getId())) {
            challenge.consume(now);
            saveUntil(challenge, challenge.getExpiresAt(), now);
            throw recoverableAccountNotFound();
        }

        String completionToken = secretGenerator.generateCompletionToken();
        Duration tokenValidity = Duration.ofMinutes(properties.getCompletionTokenValidityMinutes());
        challenge.complete(secretGenerator.hashToken(completionToken), now, tokenValidity);
        challengeRepository.save(challenge, tokenValidity);
        return new PasswordResetConfirmedResponse(
                completionToken,
                tokenValidity.toSeconds(),
                recoveryUser.getLoginId()
        );
    }

    public Long consumePasswordResetToken(String loginId, String email, String completionToken) {
        EmailVerificationChallenge challenge = challengeRepository
                .findByChallengeKey(passwordResetChallengeKey(loginId, email))
                .orElseThrow(() -> invalidCompletionToken());
        consumeToken(challenge, completionToken);
        return challenge.getUserId();
    }

    public EmailVerificationConfirmedResponse confirmSignupCode(String email, String code) {
        EmailVerificationChallenge challenge = challengeRepository
                .findByChallengeKey(signupChallengeKey(email))
                .orElseThrow(() -> invalidVerificationCode());
        LocalDateTime now = LocalDateTime.now();
        if (!challenge.canVerify(now, properties.getMaxVerificationAttempts())) {
            throw new BusinessException(HttpStatus.GONE, "email_verification_expired");
        }
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            saveUntil(challenge, challenge.getExpiresAt(), now);
            throw invalidVerificationCode();
        }

        String completionToken = secretGenerator.generateCompletionToken();
        Duration tokenValidity = Duration.ofMinutes(properties.getCompletionTokenValidityMinutes());
        challenge.complete(secretGenerator.hashToken(completionToken), now, tokenValidity);
        challengeRepository.save(challenge, tokenValidity);
        return new EmailVerificationConfirmedResponse(completionToken, tokenValidity.toSeconds());
    }

    public EmailVerificationConfirmedResponse confirmProfileChangeCode(Long userId, String email, String code) {
        EmailVerificationChallenge challenge = challengeRepository
                .findByChallengeKey(profileChangeChallengeKey(userId, email))
                .orElseThrow(() -> invalidVerificationCode());
        return confirmCode(challenge, code);
    }

    public void consumeSignupToken(String email, String completionToken) {
        EmailVerificationChallenge challenge = challengeRepository
                .findByChallengeKey(signupChallengeKey(email))
                .orElseThrow(() -> invalidCompletionToken());
        String tokenHash = secretGenerator.hashToken(completionToken);
        LocalDateTime now = LocalDateTime.now();
        if (!challenge.canConsume(tokenHash, now)) {
            throw invalidCompletionToken();
        }
        challenge.consume(now);
        saveUntil(challenge, challenge.getCompletionTokenExpiresAt(), now);
    }

    public void consumeProfileChangeToken(Long userId, String email, String completionToken) {
        EmailVerificationChallenge challenge = challengeRepository
                .findByChallengeKey(profileChangeChallengeKey(userId, email))
                .orElseThrow(() -> invalidCompletionToken());
        consumeToken(challenge, completionToken);
    }

    public String signupChallengeKey(String email) {
        return "signup:" + email;
    }

    public String profileChangeChallengeKey(Long userId, String email) {
        return "profile:" + userId + ":" + email;
    }

    public String loginIdRecoveryChallengeKey(String email) {
        return "login-id-recovery:" + email;
    }

    public String passwordResetChallengeKey(String loginId, String email) {
        return "password-reset:" + loginId + ":" + email;
    }

    private EmailVerificationConfirmedResponse confirmCode(
            EmailVerificationChallenge challenge,
            String code
    ) {
        LocalDateTime now = LocalDateTime.now();
        if (!challenge.canVerify(now, properties.getMaxVerificationAttempts())) {
            throw new BusinessException(HttpStatus.GONE, "email_verification_expired");
        }
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            saveUntil(challenge, challenge.getExpiresAt(), now);
            throw invalidVerificationCode();
        }

        String completionToken = secretGenerator.generateCompletionToken();
        Duration tokenValidity = Duration.ofMinutes(properties.getCompletionTokenValidityMinutes());
        challenge.complete(secretGenerator.hashToken(completionToken), now, tokenValidity);
        challengeRepository.save(challenge, tokenValidity);
        return new EmailVerificationConfirmedResponse(completionToken, tokenValidity.toSeconds());
    }

    private void consumeToken(EmailVerificationChallenge challenge, String completionToken) {
        String tokenHash = secretGenerator.hashToken(completionToken);
        LocalDateTime now = LocalDateTime.now();
        if (!challenge.canConsume(tokenHash, now)) {
            throw invalidCompletionToken();
        }
        challenge.consume(now);
        saveUntil(challenge, challenge.getCompletionTokenExpiresAt(), now);
    }

    private void verifyAndConsumeCode(
            EmailVerificationChallenge challenge,
            String code,
            LocalDateTime now
    ) {
        verifyCode(challenge, code, now);
        challenge.consume(now);
        saveUntil(challenge, challenge.getExpiresAt(), now);
    }

    private void verifyCode(EmailVerificationChallenge challenge, String code, LocalDateTime now) {
        if (!challenge.canVerify(now, properties.getMaxVerificationAttempts())) {
            throw new BusinessException(HttpStatus.GONE, "email_verification_expired");
        }
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            saveUntil(challenge, challenge.getExpiresAt(), now);
            throw invalidVerificationCode();
        }
    }

    private User findPasswordResetUser(String loginId, String email) {
        return userRepository.findByLoginId(loginId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(User::hasVerifiedEmail)
                .filter(user -> Objects.equals(user.getEmail(), email))
                .orElse(null);
    }

    private void saveUntil(
            EmailVerificationChallenge challenge,
            LocalDateTime expiresAt,
            LocalDateTime now
    ) {
        challengeRepository.save(challenge, Duration.between(now, expiresAt));
    }

    private BusinessException invalidVerificationCode() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_email_verification_code");
    }

    private BusinessException invalidCompletionToken() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_email_verification_token");
    }

    private BusinessException recoverableAccountNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "recoverable_account_not_found");
    }
}
