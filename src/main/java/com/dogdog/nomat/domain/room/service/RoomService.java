package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.room.dto.CreateRoomRequest;
import com.dogdog.nomat.domain.room.dto.CreateRoomResponse;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final TimeLimitMode DEFAULT_TIME_LIMIT_MODE = TimeLimitMode.FIXED;
    private static final boolean DEFAULT_AUDIO_REPEAT_ENABLED = true;
    private static final boolean DEFAULT_INITIAL_HINT_ENABLED = true;
    private static final int DEFAULT_INITIAL_HINT_TRIGGER_SECONDS = 10;

    private final UserRepository userRepository;
    private final QuizMapRepository quizMapRepository;
    private final RoomRedisRepository roomRedisRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CreateRoomResponse createRoom(Long userId, CreateRoomRequest request) {
        User host = getAuthenticatedUser(userId);
        QuizMap map = getPublicPublishedMap(request.mapId());
        validateQuestionCount(request.selectedQuestionCount(), map);

        if (roomRedisRepository.findJoinedRoomId(userId).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "already_joined_room");
        }

        LocalDateTime now = LocalDateTime.now();
        Long roomId = roomRedisRepository.nextRoomId();
        RoomState room = RoomState.waiting(
                roomId,
                request.title(),
                map.getId(),
                map.getTitle(),
                thumbnailUrl(map),
                map.getQuestionCount(),
                map.getVersion(),
                hasPassword(request.password()),
                passwordHash(request.password()),
                request.maxPlayers(),
                request.selectedQuestionCount(),
                request.answerTimeLimitSeconds(),
                timeLimitMode(request.timeLimitMode()),
                valueOrDefault(request.audioRepeatEnabled(), DEFAULT_AUDIO_REPEAT_ENABLED),
                valueOrDefault(request.initialHintEnabled(), DEFAULT_INITIAL_HINT_ENABLED),
                valueOrDefault(request.initialHintTriggerSeconds(), DEFAULT_INITIAL_HINT_TRIGGER_SECONDS),
                hostMember(host, now),
                now
        );

        if (!roomRedisRepository.createRoom(room)) {
            throw new BusinessException(HttpStatus.CONFLICT, "already_joined_room");
        }

        return CreateRoomResponse.from(room);
    }

    private User getAuthenticatedUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token"));
    }

    private QuizMap getPublicPublishedMap(Long mapId) {
        return quizMapRepository.findByIdAndStatusAndVisibility(
                        mapId,
                        MapStatus.PUBLISHED,
                        MapVisibility.PUBLIC
                )
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "map_not_found"));
    }

    private void validateQuestionCount(Integer selectedQuestionCount, QuizMap map) {
        if (selectedQuestionCount > map.getQuestionCount()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }
    }

    private TimeLimitMode timeLimitMode(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_TIME_LIMIT_MODE;
        }

        try {
            return TimeLimitMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }
    }

    private boolean hasPassword(String password) {
        return StringUtils.hasText(password);
    }

    private String passwordHash(String password) {
        if (!hasPassword(password)) {
            return null;
        }

        return passwordEncoder.encode(password);
    }

    private RoomMember hostMember(User host, LocalDateTime joinedAt) {
        Asset profileImageAsset = host.getProfileImageAsset();
        String profileImageUrl = profileImageAsset == null ? null : profileImageAsset.getUrl();
        return new RoomMember(host.getId(), host.getNickname(), profileImageUrl, true, joinedAt);
    }

    private String thumbnailUrl(QuizMap map) {
        Asset thumbnailAsset = map.getThumbnailAsset();
        return thumbnailAsset == null ? null : thumbnailAsset.getUrl();
    }

    private boolean valueOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
