package com.dogdog.nomat.domain.auth.service;

import com.dogdog.nomat.domain.auth.dto.GuestLoginRequest;
import com.dogdog.nomat.domain.auth.dto.GuestLoginResponse;
import com.dogdog.nomat.domain.auth.dto.LoginRequest;
import com.dogdog.nomat.domain.auth.dto.LoginResponse;
import com.dogdog.nomat.domain.auth.dto.RefreshResponse;
import com.dogdog.nomat.domain.auth.dto.ResetPasswordRequest;
import com.dogdog.nomat.domain.auth.dto.SignupRequest;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUser;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUserType;
import com.dogdog.nomat.domain.auth.token.AuthTokenProvider;
import com.dogdog.nomat.domain.auth.token.TokenPair;
import com.dogdog.nomat.domain.emailverification.service.EmailAddressNormalizer;
import com.dogdog.nomat.domain.emailverification.service.EmailVerificationService;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String GUEST_USER_TYPE = "GUEST";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenProvider authTokenProvider;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public void signup(SignupRequest request) {
        validateUniqueUser(request);
        String email = normalizeEmail(request.email());
        if (email != null) {
            emailVerificationService.consumeSignupToken(email, request.emailVerificationToken());
        } else if (request.emailVerificationToken() != null && !request.emailVerificationToken().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_email_verification_token");
        }

        User user = User.create(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.nickname()
        );
        if (email != null) {
            user.verifyEmail(email, LocalDateTime.now());
        }

        userRepository.save(user);
        log.info("event=member_signup_succeeded userId={} emailVerified={}", user.getId(), email != null);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByLoginId(request.loginId())
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .filter(User::hasVerifiedEmail)
                .filter(foundUser -> Objects.equals(foundUser.getEmail(), email))
                .orElseThrow(this::invalidEmailVerificationToken);
        Long verifiedUserId = emailVerificationService.consumePasswordResetToken(
                request.loginId(),
                email,
                request.emailVerificationToken()
        );
        if (!Objects.equals(user.getId(), verifiedUserId)) {
            throw invalidEmailVerificationToken();
        }
        user.changePassword(passwordEncoder.encode(request.password()));
        log.info("event=password_reset_succeeded userId={}", user.getId());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElse(null);

        if (user == null) {
            log.warn("event=member_login_rejected reason=invalid_credentials");
            throw invalidIdOrPassword();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("event=member_login_rejected userId={} reason=invalid_credentials", user.getId());
            throw invalidIdOrPassword();
        }

        TokenPair tokenPair = authTokenProvider.issue(user);
        log.info("event=member_login_succeeded userId={}", user.getId());
        return new LoginResponse(user.getId(), tokenPair.accessToken(), tokenPair.refreshToken());
    }

    public GuestLoginResponse loginGuest(GuestLoginRequest request) {
        String nickname = request.nickname().trim();
        Long guestUserId = authTokenProvider.generateGuestUserId();
        TokenPair tokenPair = authTokenProvider.issueGuest(guestUserId, nickname);
        log.info("event=guest_login_succeeded userId={}", guestUserId);

        return new GuestLoginResponse(
                guestUserId,
                GUEST_USER_TYPE,
                nickname,
                tokenPair.accessToken(),
                tokenPair.refreshToken()
        );
    }

    public GuestLoginResponse changeGuestNickname(AuthenticatedUser authenticatedUser, GuestLoginRequest request) {
        authenticatedUser.requireGuest();

        String nickname = request.nickname().trim();
        TokenPair tokenPair = authTokenProvider.issueGuest(authenticatedUser.userId(), nickname);
        log.info("event=guest_nickname_changed userId={}", authenticatedUser.userId());

        return new GuestLoginResponse(
                authenticatedUser.userId(),
                GUEST_USER_TYPE,
                nickname,
                tokenPair.accessToken(),
                tokenPair.refreshToken()
        );
    }

    @Transactional(readOnly = true)
    public RefreshResponse refresh(String authorizationHeader) {
        String refreshToken = getBearerToken(authorizationHeader);
        return refreshWithToken(refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshResponse refreshWithToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidToken();
        }

        Jwt refreshJwt = decodeRefreshToken(refreshToken);
        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(refreshJwt);

        if (authenticatedUser.userType() == AuthenticatedUserType.GUEST) {
            log.debug("event=access_token_refreshed userId={} userType=GUEST", authenticatedUser.userId());
            return new RefreshResponse(authTokenProvider.issueGuestAccessToken(
                    authenticatedUser.userId(),
                    authenticatedUser.nickname()
            ));
        }

        User user = userRepository.findById(authenticatedUser.userId())
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(this::invalidToken);

        log.debug("event=access_token_refreshed userId={} userType=MEMBER", user.getId());
        return new RefreshResponse(authTokenProvider.issueAccessToken(user));
    }

    private void validateUniqueUser(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_login_id");
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_nickname");
        }

        String email = normalizeEmail(request.email());
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_email");
        }
    }

    private String normalizeEmail(String email) {
        return EmailAddressNormalizer.normalizeNullable(email);
    }

    private BusinessException invalidIdOrPassword() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_id_or_password");
    }

    private BusinessException invalidEmailVerificationToken() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_email_verification_token");
    }

    private BusinessException invalidToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token");
    }

    private String getBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw invalidToken();
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw invalidToken();
        }

        return token;
    }

    private Jwt decodeRefreshToken(String refreshToken) {
        try {
            return authTokenProvider.decodeRefreshToken(refreshToken);
        } catch (JwtException exception) {
            throw invalidToken();
        }
    }

}
