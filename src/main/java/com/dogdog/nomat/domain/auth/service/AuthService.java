package com.dogdog.nomat.domain.auth.service;

import com.dogdog.nomat.domain.auth.dto.GuestLoginRequest;
import com.dogdog.nomat.domain.auth.dto.GuestLoginResponse;
import com.dogdog.nomat.domain.auth.dto.LoginRequest;
import com.dogdog.nomat.domain.auth.dto.LoginResponse;
import com.dogdog.nomat.domain.auth.dto.RefreshResponse;
import com.dogdog.nomat.domain.auth.dto.SignupRequest;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUser;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUserType;
import com.dogdog.nomat.domain.auth.token.AuthTokenProvider;
import com.dogdog.nomat.domain.auth.token.TokenPair;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String GUEST_USER_TYPE = "GUEST";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenProvider authTokenProvider;

    @Transactional
    public void signup(SignupRequest request) {
        validateUniqueUser(request);

        User user = User.create(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                normalizeEmail(request.email())
        );

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(this::invalidIdOrPassword);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidIdOrPassword();
        }

        TokenPair tokenPair = authTokenProvider.issue(user);
        return new LoginResponse(user.getId(), tokenPair.accessToken(), tokenPair.refreshToken());
    }

    public GuestLoginResponse loginGuest(GuestLoginRequest request) {
        String nickname = request.nickname().trim();
        Long guestUserId = authTokenProvider.generateGuestUserId();
        TokenPair tokenPair = authTokenProvider.issueGuest(guestUserId, nickname);

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
            return new RefreshResponse(authTokenProvider.issueGuestAccessToken(
                    authenticatedUser.userId(),
                    authenticatedUser.nickname()
            ));
        }

        User user = userRepository.findById(authenticatedUser.userId())
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(this::invalidToken);

        return new RefreshResponse(authTokenProvider.issueAccessToken(user));
    }

    private void validateUniqueUser(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_login_id");
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_nickname");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return email.trim();
    }

    private BusinessException invalidIdOrPassword() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_id_or_password");
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
