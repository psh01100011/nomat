package com.dogdog.nomat.domain.emailverification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.emailverification.config.EmailVerificationProperties;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationConfirmedResponse;
import com.dogdog.nomat.domain.emailverification.entity.EmailVerificationChallenge;
import com.dogdog.nomat.domain.emailverification.entity.EmailVerificationPurpose;
import com.dogdog.nomat.domain.emailverification.repository.EmailVerificationChallengeRepository;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class EmailVerificationChallengeManagerTest {

    @Mock
    private EmailVerificationChallengeRepository challengeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationMailSender mailSender;

    @Mock
    private EmailVerificationSecretGenerator secretGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    private final EmailVerificationProperties properties = new EmailVerificationProperties();

    private EmailVerificationChallengeManager challengeManager;

    @BeforeEach
    void setUp() {
        properties.setCodeValidityMinutes(10);
        properties.setCompletionTokenValidityMinutes(30);
        properties.setMaxVerificationAttempts(5);
        challengeManager = new EmailVerificationChallengeManager(
                challengeRepository,
                userRepository,
                mailSender,
                secretGenerator,
                properties,
                passwordEncoder
        );
    }

    @Test
    void issueSignupCodeStoresHashAndSendsPlainCode() {
        given(challengeRepository.findByChallengeKey("signup:tester@example.com"))
                .willReturn(Optional.empty());
        given(secretGenerator.generateCode()).willReturn("123456");
        given(passwordEncoder.encode("123456")).willReturn("code-hash");

        var response = challengeManager.issueSignupCode("tester@example.com");

        ArgumentCaptor<EmailVerificationChallenge> challengeCaptor =
                ArgumentCaptor.forClass(EmailVerificationChallenge.class);
        verify(challengeRepository).save(challengeCaptor.capture(), any(Duration.class));
        verify(mailSender).sendVerificationCode("tester@example.com", "123456");
        assertThat(challengeCaptor.getValue().getCodeHash()).isEqualTo("code-hash");
        assertThat(response.expiresInSeconds()).isEqualTo(600);
        assertThat(response.resendAvailableInSeconds()).isZero();
    }

    @Test
    void confirmSignupCodeCountsIncorrectAttempt() {
        EmailVerificationChallenge challenge = challenge();
        given(challengeRepository.findByChallengeKey("signup:tester@example.com"))
                .willReturn(Optional.of(challenge));
        given(passwordEncoder.matches("000000", "code-hash")).willReturn(false);

        assertThatThrownBy(() -> challengeManager.confirmSignupCode("tester@example.com", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_email_verification_code");

        assertThat(challenge.getFailedAttemptCount()).isEqualTo(1);
    }

    @Test
    void confirmAndConsumeSignupToken() {
        EmailVerificationChallenge challenge = challenge();
        given(challengeRepository.findByChallengeKey("signup:tester@example.com"))
                .willReturn(Optional.of(challenge));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);
        given(secretGenerator.generateCompletionToken()).willReturn("completion-token");
        given(secretGenerator.hashToken("completion-token")).willReturn("token-hash");

        EmailVerificationConfirmedResponse response =
                challengeManager.confirmSignupCode("tester@example.com", "123456");

        assertThat(response.emailVerificationToken()).isEqualTo("completion-token");
        assertThat(response.expiresInSeconds()).isEqualTo(1800);

        challengeManager.consumeSignupToken("tester@example.com", "completion-token");

        assertThat(challenge.getConsumedAt()).isNotNull();
    }

    private EmailVerificationChallenge challenge() {
        return EmailVerificationChallenge.create(
                "signup:tester@example.com",
                "tester@example.com",
                EmailVerificationPurpose.SIGNUP,
                null,
                "code-hash",
                LocalDateTime.now(),
                Duration.ofMinutes(10)
        );
    }
}
