package com.dogdog.nomat.domain.auth.service;

import com.dogdog.nomat.domain.auth.dto.LoginRequest;
import com.dogdog.nomat.domain.auth.dto.LoginResponse;
import com.dogdog.nomat.domain.auth.dto.RefreshResponse;
import com.dogdog.nomat.domain.auth.dto.SignupRequest;
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
        Long userId = getUserId(refreshJwt);

        User user = userRepository.findById(userId)
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

    private Long getUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        if (userId == null) {
            throw invalidToken();
        }

        return userId.longValue();
    }
}
