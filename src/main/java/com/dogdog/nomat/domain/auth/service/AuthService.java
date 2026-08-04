package com.dogdog.nomat.domain.auth.service;

import com.dogdog.nomat.domain.auth.dto.LoginRequest;
import com.dogdog.nomat.domain.auth.dto.LoginResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenProvider authTokenProvider;

    @Transactional
    public void signup(SignupRequest request) {
        validateUniqueUser(request);

        User user = User.create(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.nickname()
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

    private void validateUniqueUser(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_login_id");
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_nickname");
        }
    }

    private BusinessException invalidIdOrPassword() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_id_or_password");
    }
}
