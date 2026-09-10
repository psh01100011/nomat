package com.dogdog.nomat.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.auth.dto.LoginRequest;
import com.dogdog.nomat.domain.auth.dto.LoginResponse;
import com.dogdog.nomat.domain.auth.dto.RefreshResponse;
import com.dogdog.nomat.domain.auth.dto.SignupRequest;
import com.dogdog.nomat.domain.auth.token.AuthTokenProvider;
import com.dogdog.nomat.domain.auth.token.TokenPair;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthTokenProvider authTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void signupCreatesUserWithEncodedPassword() {
        SignupRequest request = new SignupRequest(
                "testuser",
                "password123!",
                "password123!",
                "tester",
                " tester@example.com "
        );
        given(passwordEncoder.encode("password123!")).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        authService.signup(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getLoginId()).isEqualTo("testuser");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getNickname()).isEqualTo("tester");
        assertThat(savedUser.getEmail()).isEqualTo("tester@example.com");
    }

    @Test
    void signupRejectsDuplicateLoginId() {
        SignupRequest request = new SignupRequest("testuser", "password123!", "password123!", "tester", null);
        given(userRepository.existsByLoginId("testuser")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duplicate_login_id");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signupRejectsDuplicateNickname() {
        SignupRequest request = new SignupRequest("testuser", "password123!", "password123!", "tester", null);
        given(userRepository.existsByNickname("tester")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duplicate_nickname");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginReturnsTokens() {
        LoginRequest request = new LoginRequest("testuser", "password123!");
        User user = User.create("testuser", "encoded-password", "tester");
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("testuser")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded-password")).willReturn(true);
        given(authTokenProvider.issue(user)).willReturn(new TokenPair("access-token", "refresh-token"));

        LoginResponse response = authService.login(request);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginRejectsUnknownLoginId() {
        LoginRequest request = new LoginRequest("testuser", "password123!");
        given(userRepository.findByLoginId("testuser")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_id_or_password");
    }

    @Test
    void loginRejectsWrongPassword() {
        LoginRequest request = new LoginRequest("testuser", "password123!");
        User user = User.create("testuser", "encoded-password", "tester");

        given(userRepository.findByLoginId("testuser")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_id_or_password");
    }

    @Test
    void refreshReturnsNewAccessToken() {
        User user = User.create("testuser", "encoded-password", "tester");
        ReflectionTestUtils.setField(user, "id", 1L);
        Jwt refreshJwt = refreshJwt(1L);

        given(authTokenProvider.decodeRefreshToken("refresh-token")).willReturn(refreshJwt);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(authTokenProvider.issueAccessToken(user)).willReturn("new-access-token");

        RefreshResponse response = authService.refresh("Bearer refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
    }

    @Test
    void refreshRejectsMissingBearerToken() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }

    @Test
    void refreshRejectsInvalidRefreshToken() {
        given(authTokenProvider.decodeRefreshToken("invalid-refresh-token"))
                .willThrow(new BadJwtException("invalid"));

        assertThatThrownBy(() -> authService.refresh("Bearer invalid-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }

    @Test
    void refreshRejectsUnknownUserId() {
        Jwt refreshJwt = refreshJwt(1L);
        given(authTokenProvider.decodeRefreshToken("refresh-token")).willReturn(refreshJwt);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("Bearer refresh-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }

    private Jwt refreshJwt(Long userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("refresh-token")
                .header("alg", "HS256")
                .issuer("nomat")
                .subject("testuser")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("userId", userId)
                .claim("tokenType", "refresh")
                .build();
    }
}
