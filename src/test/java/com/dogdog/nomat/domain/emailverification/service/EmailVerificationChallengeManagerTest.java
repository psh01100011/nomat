package com.dogdog.nomat.domain.emailverification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.dogdog.nomat.domain.emailverification.config.EmailVerificationProperties;
import com.dogdog.nomat.domain.emailverification.dto.EmailVerificationConfirmedResponse;
import com.dogdog.nomat.domain.emailverification.entity.EmailVerificationChallenge;
import com.dogdog.nomat.domain.emailverification.entity.EmailVerificationPurpose;
import com.dogdog.nomat.domain.emailverification.repository.EmailVerificationChallengeRepository;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.domain.user.entity.User;
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
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void loginIdRecoveryReturnsFullLoginIdOnlyAfterValidCode() {
        User user = verifiedUser(7L, "recover_me", "tester@example.com");
        given(userRepository.findByEmail("tester@example.com")).willReturn(Optional.of(user));
        given(challengeRepository.findByChallengeKey("login-id-recovery:tester@example.com"))
                .willReturn(Optional.empty());
        given(secretGenerator.generateCode()).willReturn("123456");
        given(passwordEncoder.encode("123456")).willReturn("code-hash");

        challengeManager.issueLoginIdRecoveryCode("tester@example.com");

        ArgumentCaptor<EmailVerificationChallenge> challengeCaptor =
                ArgumentCaptor.forClass(EmailVerificationChallenge.class);
        verify(challengeRepository).save(challengeCaptor.capture(), any(Duration.class));
        verify(mailSender).sendVerificationCode("tester@example.com", "123456");

        EmailVerificationChallenge challenge = challengeCaptor.getValue();
        given(challengeRepository.findByChallengeKey("login-id-recovery:tester@example.com"))
                .willReturn(Optional.of(challenge));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        var response = challengeManager.confirmLoginIdRecoveryCode("tester@example.com", "123456");

        assertThat(response.loginId()).isEqualTo("recover_me");
        assertThat(challenge.getConsumedAt()).isNotNull();
    }

    @Test
    void loginIdRecoverySendsCodeEvenForUnknownEmail() {
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());
        given(challengeRepository.findByChallengeKey("login-id-recovery:unknown@example.com"))
                .willReturn(Optional.empty());
        given(secretGenerator.generateCode()).willReturn("123456");
        given(passwordEncoder.encode("123456")).willReturn("code-hash");

        var response = challengeManager.issueLoginIdRecoveryCode("unknown@example.com");

        assertThat(response.expiresInSeconds()).isEqualTo(600);
        assertThat(response.resendAvailableInSeconds()).isZero();
        verify(challengeRepository).save(any(EmailVerificationChallenge.class), any(Duration.class));
        verify(mailSender).sendVerificationCode("unknown@example.com", "123456");
    }

    @Test
    void loginIdRecoveryReportsUnknownAccountAfterConfirmation() {
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());
        given(challengeRepository.findByChallengeKey("login-id-recovery:unknown@example.com"))
                .willReturn(Optional.empty());
        given(secretGenerator.generateCode()).willReturn("123456");
        given(passwordEncoder.encode("123456")).willReturn("code-hash");

        challengeManager.issueLoginIdRecoveryCode("unknown@example.com");

        ArgumentCaptor<EmailVerificationChallenge> challengeCaptor =
                ArgumentCaptor.forClass(EmailVerificationChallenge.class);
        verify(challengeRepository).save(challengeCaptor.capture(), any(Duration.class));
        given(challengeRepository.findByChallengeKey("login-id-recovery:unknown@example.com"))
                .willReturn(Optional.of(challengeCaptor.getValue()));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);

        assertThatThrownBy(() -> challengeManager.confirmLoginIdRecoveryCode("unknown@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recoverable_account_not_found");
    }

    @Test
    void loginIdRecoveryPropagatesDeliveryFailure() {
        User user = verifiedUser(7L, "recover_me", "tester@example.com");
        given(userRepository.findByEmail("tester@example.com")).willReturn(Optional.of(user));
        given(challengeRepository.findByChallengeKey("login-id-recovery:tester@example.com"))
                .willReturn(Optional.empty());
        given(secretGenerator.generateCode()).willReturn("123456");
        given(passwordEncoder.encode("123456")).willReturn("code-hash");
        willThrow(new EmailVerificationMailException(new RuntimeException("delivery failed")))
                .given(mailSender).sendVerificationCode("tester@example.com", "123456");

        assertThatThrownBy(() -> challengeManager.issueLoginIdRecoveryCode("tester@example.com"))
                .isInstanceOf(EmailVerificationMailException.class);
        verify(challengeRepository, never()).save(any(EmailVerificationChallenge.class), any(Duration.class));
    }

    @Test
    void passwordResetCodeIssuesAndConsumesOneTimeToken() {
        User user = verifiedUser(7L, "recover_me", "tester@example.com");
        String challengeKey = "password-reset:recover_me:tester@example.com";
        given(userRepository.findByLoginId("recover_me")).willReturn(Optional.of(user));
        given(challengeRepository.findByChallengeKey(challengeKey)).willReturn(Optional.empty());
        given(secretGenerator.generateCode()).willReturn("123456");
        given(passwordEncoder.encode("123456")).willReturn("code-hash");

        challengeManager.issuePasswordResetCode("recover_me", "tester@example.com");

        ArgumentCaptor<EmailVerificationChallenge> challengeCaptor =
                ArgumentCaptor.forClass(EmailVerificationChallenge.class);
        verify(challengeRepository).save(challengeCaptor.capture(), any(Duration.class));
        EmailVerificationChallenge challenge = challengeCaptor.getValue();
        given(challengeRepository.findByChallengeKey(challengeKey)).willReturn(Optional.of(challenge));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);
        given(secretGenerator.generateCompletionToken()).willReturn("completion-token");
        given(secretGenerator.hashToken("completion-token")).willReturn("token-hash");

        var response = challengeManager.confirmPasswordResetCode(
                "recover_me",
                "tester@example.com",
                "123456"
        );

        assertThat(response.emailVerificationToken()).isEqualTo("completion-token");
        assertThat(response.loginId()).isEqualTo("recover_me");

        Long userId = challengeManager.consumePasswordResetToken(
                "recover_me",
                "tester@example.com",
                "completion-token"
        );

        assertThat(userId).isEqualTo(7L);
        assertThat(challenge.getConsumedAt()).isNotNull();

        assertThatThrownBy(() -> challengeManager.consumePasswordResetToken(
                "recover_me",
                "tester@example.com",
                "completion-token"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_email_verification_token");
    }

    @Test
    void passwordResetSendsCodeThenReportsUnknownAccountAfterConfirmation() {
        String challengeKey = "password-reset:unknown:unknown@example.com";
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());
        given(challengeRepository.findByChallengeKey(challengeKey)).willReturn(Optional.empty());
        given(secretGenerator.generateCode()).willReturn("123456");
        given(passwordEncoder.encode("123456")).willReturn("code-hash");

        var response = challengeManager.issuePasswordResetCode("unknown", "unknown@example.com");

        assertThat(response.expiresInSeconds()).isEqualTo(600);
        assertThat(response.resendAvailableInSeconds()).isZero();
        verify(challengeRepository).save(any(EmailVerificationChallenge.class), any(Duration.class));
        verify(mailSender).sendVerificationCode("unknown@example.com", "123456");

        ArgumentCaptor<EmailVerificationChallenge> challengeCaptor =
                ArgumentCaptor.forClass(EmailVerificationChallenge.class);
        verify(challengeRepository).save(challengeCaptor.capture(), any(Duration.class));
        given(challengeRepository.findByChallengeKey(challengeKey))
                .willReturn(Optional.of(challengeCaptor.getValue()));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);

        assertThatThrownBy(() -> challengeManager.confirmPasswordResetCode(
                "unknown",
                "unknown@example.com",
                "123456"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recoverable_account_not_found");
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

    private User verifiedUser(Long id, String loginId, String email) {
        User user = User.create(loginId, "encoded-password", "tester");
        ReflectionTestUtils.setField(user, "id", id);
        user.verifyEmail(email, LocalDateTime.now());
        return user;
    }
}
