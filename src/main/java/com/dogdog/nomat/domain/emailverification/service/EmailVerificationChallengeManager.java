package com.dogdog.nomat.domain.emailverification.service;

import com.dogdog.nomat.domain.emailverification.config.EmailVerificationProperties;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationConfirmedResponse;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationSentResponse;
import com.dogdog.nomat.domain.emailverification.entity.EmailVerificationChallenge;
import com.dogdog.nomat.domain.emailverification.entity.EmailVerificationPurpose;
import com.dogdog.nomat.domain.emailverification.repository.EmailVerificationChallengeRepository;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
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
}
