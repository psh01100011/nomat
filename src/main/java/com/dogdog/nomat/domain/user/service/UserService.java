package com.dogdog.nomat.domain.user.service;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetProcessingStatus;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.entity.AssetType;
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
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenProvider authTokenProvider;

    @Transactional(readOnly = true)
    public MyInfoResponse getMyInfo(Long userId) {
        User user = getAuthenticatedUser(userId);

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

    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request"));

        return UserInfoResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PasswordVerificationResponse verifyPassword(Long userId, VerifyPasswordRequest request) {
        User user = getAuthenticatedUser(userId);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_id_or_password");
        }

        return new PasswordVerificationResponse(authTokenProvider.issuePasswordVerificationToken(user));
    }

    @Transactional
    public void modifyMyInfo(Long userId, ModifyMyInfoRequest request) {
        User user = getAuthenticatedUser(userId);

        String nickname = getNicknameToUpdate(user, request.nickname());
        Asset profileImageAsset = getProfileImageToUpdate(user, request.profileImageAssetId());

        user.changeProfile(nickname, profileImageAsset);
    }

    @Transactional
    public void modifyPassword(Long userId, ModifyPasswordRequest request) {
        User user = getAuthenticatedUser(userId);
        validatePasswordVerificationToken(user, request.passwordVerificationToken());

        user.changePassword(passwordEncoder.encode(request.password()));
    }

    @Transactional
    public void deleteMyAccount(Long userId) {
        User user = getAuthenticatedUser(userId);

        user.delete();
    }

    private User getAuthenticatedUser(Long userId) {
        return userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token"));
    }

    private String getNicknameToUpdate(User user, String nickname) {
        if (nickname == null) {
            return user.getNickname();
        }

        String normalizedNickname = nickname.trim();
        if (!StringUtils.hasText(normalizedNickname)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_nickname_length");
        }

        if (normalizedNickname.length() < 2 || normalizedNickname.length() > 12) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_nickname_length");
        }

        if (!normalizedNickname.matches("^[가-힣A-Za-z0-9_]+$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_nickname_format");
        }

        if (user.getNickname().equals(normalizedNickname)) {
            return user.getNickname();
        }

        if (userRepository.existsByNicknameAndIdNot(normalizedNickname, user.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_nickname");
        }

        return normalizedNickname;
    }

    private Asset getProfileImageToUpdate(User user, Long profileImageAssetId) {
        if (profileImageAssetId == null) {
            return user.getProfileImageAsset();
        }

        Asset asset = assetRepository.findById(profileImageAssetId)
                .orElseThrow(this::invalidRequest);
        validateProfileImageAsset(user, asset);
        asset.attach();

        return asset;
    }

    private void validateProfileImageAsset(User user, Asset asset) {
        if (asset.getAssetType() != AssetType.IMAGE
                || asset.getStatus() == AssetStatus.DELETED
                || asset.getProcessingStatus() != AssetProcessingStatus.READY
                || !Objects.equals(asset.getUploader().getId(), user.getId())) {
            throw invalidRequest();
        }
    }

    private void validatePasswordVerificationToken(User user, String passwordVerificationToken) {
        Jwt jwt;
        try {
            jwt = authTokenProvider.decodePasswordVerificationToken(passwordVerificationToken);
        } catch (JwtException exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token");
        }

        Number tokenUserId = jwt.getClaim("userId");
        if (tokenUserId == null || !Objects.equals(tokenUserId.longValue(), user.getId())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token");
        }
    }

    private BusinessException invalidRequest() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
    }
}
