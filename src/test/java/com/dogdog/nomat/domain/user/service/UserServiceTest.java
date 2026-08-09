package com.dogdog.nomat.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.dogdog.nomat.domain.user.dto.AvailabilityResponse;
import com.dogdog.nomat.domain.user.dto.MyInfoResponse;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

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
}
