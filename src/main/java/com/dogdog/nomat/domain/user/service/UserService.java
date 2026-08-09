package com.dogdog.nomat.domain.user.service;

import com.dogdog.nomat.domain.user.dto.AvailabilityResponse;
import com.dogdog.nomat.domain.user.dto.MyInfoResponse;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MyInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token"));

        return MyInfoResponse.from(user);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkLoginId(String loginId) {
        return new AvailabilityResponse(!userRepository.existsByLoginId(loginId));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkNickname(String nickname) {
        return new AvailabilityResponse(!userRepository.existsByNickname(nickname));
    }
}
