package com.dogdog.nomat.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.dogdog.nomat.domain.user.dto.AvailabilityResponse;
import com.dogdog.nomat.domain.user.dto.MyInfoResponse;
import com.dogdog.nomat.domain.user.dto.UserInfoResponse;
import com.dogdog.nomat.domain.user.dto.VerifyPasswordRequest;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void getMyInfoReturnsCurrentUserInfo() {
        User user = User.create("testuser", "encoded-password", "tester");
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyInfoResponse response = userService.getMyInfo(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("tester");
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    void getMyInfoRejectsUnknownUserId() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyInfo(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }

    @Test
    void checkLoginIdReturnsAvailableTrueWhenLoginIdDoesNotExist() {
        given(userRepository.existsByLoginId("testuser")).willReturn(false);

        AvailabilityResponse response = userService.checkLoginId("testuser");

        assertThat(response.available()).isTrue();
    }

    @Test
    void checkLoginIdReturnsAvailableFalseWhenLoginIdExists() {
        given(userRepository.existsByLoginId("testuser")).willReturn(true);

        AvailabilityResponse response = userService.checkLoginId("testuser");

        assertThat(response.available()).isFalse();
    }

    @Test
    void checkNicknameReturnsAvailableTrueWhenNicknameDoesNotExist() {
        given(userRepository.existsByNickname("tester")).willReturn(false);

        AvailabilityResponse response = userService.checkNickname("tester");

        assertThat(response.available()).isTrue();
    }

    @Test
    void checkNicknameReturnsAvailableFalseWhenNicknameExists() {
        given(userRepository.existsByNickname("tester")).willReturn(true);

        AvailabilityResponse response = userService.checkNickname("tester");

        assertThat(response.available()).isFalse();
    }

    @Test
    void getUserInfoReturnsActiveUserInfo() {
        User user = User.create("testuser", "encoded-password", "tester");
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserInfoResponse response = userService.getUserInfo(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("tester");
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    void getUserInfoRejectsUnknownUserId() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserInfo(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void verifyPasswordSucceedsWhenPasswordMatches() {
        User user = User.create("testuser", "encoded-password", "tester");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded-password")).willReturn(true);

        userService.verifyPassword(1L, new VerifyPasswordRequest("password123!"));
    }

    @Test
    void verifyPasswordRejectsWrongPassword() {
        User user = User.create("testuser", "encoded-password", "tester");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> userService.verifyPassword(1L, new VerifyPasswordRequest("password123!")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_id_or_password");
    }

    @Test
    void verifyPasswordRejectsUnknownUserId() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.verifyPassword(1L, new VerifyPasswordRequest("password123!")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }
}
