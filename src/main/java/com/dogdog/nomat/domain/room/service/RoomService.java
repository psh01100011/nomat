package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.room.dto.CreateRoomRequest;
import com.dogdog.nomat.domain.room.dto.CreateRoomResponse;
import com.dogdog.nomat.domain.room.dto.JoinRoomRequest;
import com.dogdog.nomat.domain.room.dto.RoomDetailResponse;
import com.dogdog.nomat.domain.room.dto.RoomListResponse;
import com.dogdog.nomat.domain.room.model.RoomCommand;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.model.RoomTransitionResult;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
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
    private final RoomStateMachine roomStateMachine;
    private final RoomEventPublisher roomEventPublisher;
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
                categoryId(map),
                categoryName(map),
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

    @Transactional(readOnly = true)
    public RoomListResponse getRooms(
            String keyword,
            Long mapId,
            Long categoryId,
            String statusValue,
            boolean joinableOnly,
            int page,
            int size,
            String sort
    ) {
        validatePage(page, size);
        validateSort(sort);
        RoomStatus status = parseRoomStatusOrNull(statusValue);
        String normalizedKeyword = normalizeKeyword(keyword);

        List<RoomState> filteredRooms = roomRedisRepository.findRooms(sort).stream()
                .filter(room -> isListableStatus(room.status()))
                .filter(room -> status == null || room.status() == status)
                .filter(room -> mapId == null || room.mapId().equals(mapId))
                .filter(room -> categoryId == null || categoryId.equals(room.categoryId()))
                .filter(room -> normalizedKeyword == null || matchesKeyword(room, normalizedKeyword))
                .filter(room -> !joinableOnly || isJoinable(room))
                .toList();

        int fromIndex = Math.min(page * size, filteredRooms.size());
        int toIndex = Math.min(fromIndex + size, filteredRooms.size());
        return RoomListResponse.of(
                filteredRooms.subList(fromIndex, toIndex),
                page,
                size,
                filteredRooms.size()
        );
    }

    @Transactional(readOnly = true)
    public RoomDetailResponse getRoom(Long roomId) {
        RoomState room = roomRedisRepository.findById(roomId)
                .filter(foundRoom -> isListableStatus(foundRoom.status()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));

        return RoomDetailResponse.from(room);
    }

    @Transactional
    public RoomDetailResponse joinRoom(Long userId, Long roomId, JoinRoomRequest request) {
        User user = getAuthenticatedUser(userId);

        if (roomRedisRepository.findJoinedRoomId(userId).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "already_joined_room");
        }

        RoomState room = roomRedisRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));

        validateRoomPassword(room, request);

        LocalDateTime now = LocalDateTime.now();
        RoomTransitionResult result = roomStateMachine.transition(
                room,
                RoomCommand.join(roomMember(user, false, now), now)
        );

        if (!roomRedisRepository.saveJoinedRoom(result.room(), userId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "already_joined_room");
        }

        roomEventPublisher.publish(result.events());
        return RoomDetailResponse.from(result.room());
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

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }
    }

    private void validateSort(String sort) {
        if (!"latest".equals(sort) && !"players".equals(sort)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }
    }

    private RoomStatus parseRoomStatusOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            RoomStatus status = RoomStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (!isListableStatus(status)) {
                throw new IllegalArgumentException();
            }
            return status;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }
    }

    private boolean isListableStatus(RoomStatus status) {
        return status == RoomStatus.WAITING || status == RoomStatus.PLAYING;
    }

    private boolean isJoinable(RoomState room) {
        return room.status() == RoomStatus.WAITING && !room.isFull();
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesKeyword(RoomState room, String normalizedKeyword) {
        return containsKeyword(room.title(), normalizedKeyword)
                || containsKeyword(room.mapTitle(), normalizedKeyword);
    }

    private boolean containsKeyword(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
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
        return roomMember(host, true, joinedAt);
    }

    private RoomMember roomMember(User user, boolean host, LocalDateTime joinedAt) {
        Asset profileImageAsset = user.getProfileImageAsset();
        String profileImageUrl = profileImageAsset == null ? null : profileImageAsset.getUrl();
        return new RoomMember(user.getId(), user.getNickname(), profileImageUrl, host, joinedAt);
    }

    private void validateRoomPassword(RoomState room, JoinRoomRequest request) {
        if (!room.hasPassword()) {
            return;
        }

        String password = request == null ? null : request.password();
        if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, room.passwordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_room_password");
        }
    }

    private String thumbnailUrl(QuizMap map) {
        Asset thumbnailAsset = map.getThumbnailAsset();
        return thumbnailAsset == null ? null : thumbnailAsset.getUrl();
    }

    private Long categoryId(QuizMap map) {
        Category category = map.getCategory();
        return category == null ? null : category.getId();
    }

    private String categoryName(QuizMap map) {
        Category category = map.getCategory();
        return category == null ? null : category.getName();
    }

    private boolean valueOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
