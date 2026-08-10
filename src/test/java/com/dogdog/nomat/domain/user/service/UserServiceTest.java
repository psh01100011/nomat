package com.dogdog.nomat.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetProcessingStatus;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.auth.token.AuthTokenProvider;
import com.dogdog.nomat.domain.user.dto.AvailabilityResponse;
import com.dogdog.nomat.domain.user.dto.ModifyMyInfoRequest;
import com.dogdog.nomat.domain.user.dto.ModifyPasswordRequest;
import com.dogdog.nomat.domain.user.dto.MyInfoResponse;
import com.dogdog.nomat.domain.user.dto.PasswordVerificationResponse;
import com.dogdog.nomat.domain.user.dto.UserInfoResponse;
import com.dogdog.nomat.domain.user.dto.VerifyPasswordRequest;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthTokenProvider authTokenProvider;

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
        given(authTokenProvider.issuePasswordVerificationToken(user)).willReturn("password-verification-token");

        PasswordVerificationResponse response = userService.verifyPassword(1L, new VerifyPasswordRequest("password123!"));

        assertThat(response.passwordVerificationToken()).isEqualTo("password-verification-token");
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

    @Test
    void modifyMyInfoChangesNickname() {
        User user = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("newbie", 1L)).willReturn(false);

        userService.modifyMyInfo(1L, new ModifyMyInfoRequest("newbie", null));

        assertThat(user.getNickname()).isEqualTo("newbie");
        assertThat(user.getProfileImageAsset()).isNull();
    }

    @Test
    void modifyMyInfoRejectsDuplicateNickname() {
        User user = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("newbie", 1L)).willReturn(true);

        assertThatThrownBy(() -> userService.modifyMyInfo(1L, new ModifyMyInfoRequest("newbie", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duplicate_nickname");
    }

    @Test
    void modifyMyInfoChangesProfileImageAndAttachesAsset() {
        User user = activeUser(1L);
        Asset asset = imageAsset(10L, user);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(assetRepository.findById(10L)).willReturn(Optional.of(asset));

        userService.modifyMyInfo(1L, new ModifyMyInfoRequest(null, 10L));

        assertThat(user.getProfileImageAsset()).isEqualTo(asset);
        assertThat(asset.getStatus()).isEqualTo(com.dogdog.nomat.domain.asset.entity.AssetStatus.ATTACHED);
    }

    @Test
    void modifyMyInfoRejectsUnknownProfileImageAssetId() {
        User user = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(assetRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.modifyMyInfo(1L, new ModifyMyInfoRequest(null, 10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void modifyMyInfoRejectsOtherUsersProfileImageAsset() {
        User user = activeUser(1L);
        User otherUser = activeUser(2L);
        Asset asset = imageAsset(10L, otherUser);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(assetRepository.findById(10L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> userService.modifyMyInfo(1L, new ModifyMyInfoRequest(null, 10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void modifyMyInfoRejectsProfileImageAssetThatIsNotReady() {
        User user = activeUser(1L);
        Asset asset = imageAsset(10L, user);
        ReflectionTestUtils.setField(asset, "processingStatus", AssetProcessingStatus.PROCESSING);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(assetRepository.findById(10L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> userService.modifyMyInfo(1L, new ModifyMyInfoRequest(null, 10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void modifyMyInfoRejectsUnknownUserId() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.modifyMyInfo(1L, new ModifyMyInfoRequest("newbie", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }

    @Test
    void modifyPasswordChangesPasswordHash() {
        User user = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(authTokenProvider.decodePasswordVerificationToken("password-verification-token"))
                .willReturn(passwordVerificationJwt(1L));
        given(passwordEncoder.encode("newPassword123!")).willReturn("new-encoded-password");

        userService.modifyPassword(1L, new ModifyPasswordRequest("password-verification-token", "newPassword123!"));

        assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");
    }

    @Test
    void modifyPasswordRejectsInvalidPasswordVerificationToken() {
        User user = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(authTokenProvider.decodePasswordVerificationToken("invalid-token"))
                .willThrow(new BadJwtException("invalid"));

        assertThatThrownBy(() -> userService.modifyPassword(
                1L,
                new ModifyPasswordRequest("invalid-token", "newPassword123!")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }

    @Test
    void modifyPasswordRejectsOtherUsersPasswordVerificationToken() {
        User user = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(authTokenProvider.decodePasswordVerificationToken("password-verification-token"))
                .willReturn(passwordVerificationJwt(2L));

        assertThatThrownBy(() -> userService.modifyPassword(
                1L,
                new ModifyPasswordRequest("password-verification-token", "newPassword123!")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }

    @Test
    void modifyPasswordRejectsUnknownUserId() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.modifyPassword(
                1L,
                new ModifyPasswordRequest("password-verification-token", "newPassword123!")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }

    private User activeUser(Long userId) {
        User user = User.create("testuser" + userId, "encoded-password", "tester" + userId);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Asset imageAsset(Long assetId, User uploader) {
        Asset asset = Asset.createImage(
                uploader,
                "profile.png",
                "uploads/images/profile.png",
                "https://cdn.nomat.com/uploads/images/profile.png",
                "image/png",
                1024L
        );
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private Jwt passwordVerificationJwt(Long userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("password-verification-token")
                .header("alg", "HS256")
                .issuer("nomat")
                .subject("testuser")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("userId", userId)
                .claim("tokenType", "password_verification")
                .build();
    }
}
