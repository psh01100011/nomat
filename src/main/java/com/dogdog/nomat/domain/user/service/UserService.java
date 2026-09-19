package com.dogdog.nomat.domain.user.service;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetProcessingStatus;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.entity.AssetType;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.auth.model.AuthenticatedUser;
import com.dogdog.nomat.domain.auth.token.AuthTokenProvider;
import com.dogdog.nomat.domain.emailverification.service.EmailAddressNormalizer;
import com.dogdog.nomat.domain.emailverification.service.EmailVerificationService;
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
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenProvider authTokenProvider;
    private final EmailVerificationService emailVerificationService;

    @Transactional(readOnly = true)
    public MyInfoResponse getMyInfo(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.isGuest()) {
            return MyInfoResponse.fromGuest(authenticatedUser);
        }

        User user = getAuthenticatedUser(authenticatedUser.userId());
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
            log.warn("event=password_verification_rejected userId={} reason=invalid_credentials", userId);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_id_or_password");
        }

        log.debug("event=password_verification_succeeded userId={}", userId);
        return new PasswordVerificationResponse(authTokenProvider.issuePasswordVerificationToken(user));
    }

    @Transactional
    public void modifyMyInfo(Long userId, ModifyMyInfoRequest request) {
        User user = getAuthenticatedUser(userId);

        String nickname = getNicknameToUpdate(user, request.nickname());
        Asset profileImageAsset = getProfileImageToUpdate(user, request.profileImageAssetId());
        String email = getEmailToUpdate(user, request.email());
        boolean nicknameChanged = !Objects.equals(user.getNickname(), nickname);
        boolean profileImageChanged = !Objects.equals(user.getProfileImageAsset(), profileImageAsset);
        boolean emailChanged = !Objects.equals(user.getEmail(), email);
        if (emailChanged && email != null) {
            emailVerificationService.consumeProfileChangeToken(
                    userId,
                    email,
                    request.emailVerificationToken()
            );
        }

        user.changeProfile(nickname, profileImageAsset, email);
        if (emailChanged && email != null) {
            user.verifyEmail(email, LocalDateTime.now());
        }
        log.info(
                "event=member_profile_changed userId={} nicknameChanged={} profileImageChanged={} emailChanged={}",
                userId,
                nicknameChanged,
                profileImageChanged,
                emailChanged
        );
    }

    @Transactional
    public void removeProfileImage(Long userId) {
        User user = getAuthenticatedUser(userId);
        user.removeProfileImage();
        log.info("event=member_profile_image_removed userId={}", userId);
    }

    @Transactional
    public void modifyPassword(Long userId, ModifyPasswordRequest request) {
        User user = getAuthenticatedUser(userId);
        validatePasswordVerificationToken(user, request.passwordVerificationToken());

        user.changePassword(passwordEncoder.encode(request.password()));
        log.info("event=member_password_changed userId={}", userId);
    }

    @Transactional
    public void deleteMyAccount(Long userId) {
        User user = getAuthenticatedUser(userId);

        user.delete();
        log.info("event=member_account_deleted userId={}", userId);
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

        Asset asset = assetRepository.findByIdForUpdate(profileImageAssetId)
                .orElseThrow(this::invalidRequest);
        validateProfileImageAsset(user, asset);
        asset.attach();

        return asset;
    }

    private String getEmailToUpdate(User user, String email) {
        if (email == null) {
            return user.getEmail();
        }

        String normalizedEmail = EmailAddressNormalizer.normalizeNullable(email);
        if (normalizedEmail == null) {
            if (user.getEmail() != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "email_removal_not_allowed");
            }
            return null;
        }

        if (!EmailAddressNormalizer.isValid(normalizedEmail)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }

        if (!normalizedEmail.equals(user.getEmail())
                && userRepository.existsByEmailAndIdNot(normalizedEmail, user.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "duplicate_email");
        }

        return normalizedEmail;
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
