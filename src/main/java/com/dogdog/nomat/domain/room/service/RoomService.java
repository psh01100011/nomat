package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuestionAnswer;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionStatus;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuestionAnswerRepository;
import com.dogdog.nomat.domain.map.repository.QuestionMediaRepository;
import com.dogdog.nomat.domain.map.repository.QuestionRepository;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.room.dto.CreateRoomRequest;
import com.dogdog.nomat.domain.room.dto.CreateRoomResponse;
import com.dogdog.nomat.domain.room.dto.JoinRoomRequest;
import com.dogdog.nomat.domain.room.dto.RoomDetailResponse;
import com.dogdog.nomat.domain.room.dto.RoomListResponse;
import com.dogdog.nomat.domain.room.model.RoomCommand;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final QuestionMediaRepository questionMediaRepository;
    private final RoomRedisRepository roomRedisRepository;
    private final RoomStateMachine roomStateMachine;
    private final RoomEventPublisher roomEventPublisher;
    private final RoomGameProgressService roomGameProgressService;
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

    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        getAuthenticatedUser(userId);

        RoomState room = roomRedisRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_or_member_not_found"));

        RoomTransitionResult result = roomStateMachine.transition(
                room,
                RoomCommand.leave(userId, LocalDateTime.now())
        );

        roomRedisRepository.saveLeftRoom(result.room(), userId);
        roomEventPublisher.publish(result.events());
    }

    @Transactional
    public void leaveCurrentRoomByDisconnect(Long userId) {
        roomRedisRepository.findJoinedRoomId(userId)
                .ifPresent(roomId -> leaveRoomByDisconnect(userId, roomId));
    }

    @Transactional
    public void closeRoom(Long userId, Long roomId) {
        getAuthenticatedUser(userId);

        RoomState room = roomRedisRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));

        RoomTransitionResult result = roomStateMachine.transition(
                room,
                RoomCommand.close(userId, LocalDateTime.now())
        );

        roomRedisRepository.deleteRoom(result.room());
        roomEventPublisher.publish(result.events());
    }

    @Transactional
    public void kickRoomMember(Long hostUserId, Long roomId, Long targetUserId) {
        getAuthenticatedUser(hostUserId);

        RoomState room = roomRedisRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));

        RoomTransitionResult result = roomStateMachine.transition(
                room,
                RoomCommand.kick(hostUserId, targetUserId, LocalDateTime.now())
        );

        roomRedisRepository.saveKickedRoom(result.room(), targetUserId);
        roomEventPublisher.publish(result.events());
    }

    @Transactional
    public RoomDetailResponse startGame(Long hostUserId, Long roomId) {
        getAuthenticatedUser(hostUserId);

        RoomState room = roomRedisRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));

        validateMinimumPlayers(room);
        getPublicPublishedMap(room.mapId());

        String randomSeed = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        RoomTransitionResult result = roomStateMachine.transition(
                room,
                RoomCommand.start(hostUserId, randomSeed, now)
        );

        RoomGameState gameState = RoomGameState.started(
                room.roomId(),
                randomSeed,
                selectQuestions(room, randomSeed),
                room.members().stream()
                        .collect(Collectors.toMap(RoomMember::userId, ignored -> 0)),
                now
        );

        roomRedisRepository.saveStartedRoom(result.room(), gameState);
        roomEventPublisher.publish(result.events());
        roomGameProgressService.startFirstQuestion(result.room());
        return RoomDetailResponse.from(result.room());
    }

    private void leaveRoomByDisconnect(Long userId, Long roomId) {
        RoomState room = roomRedisRepository.findById(roomId).orElse(null);
        if (room == null || !room.hasMember(userId)) {
            return;
        }

        RoomTransitionResult result = roomStateMachine.transition(
                room,
                RoomCommand.leave(userId, LocalDateTime.now())
        );

        roomRedisRepository.saveLeftRoom(result.room(), userId);
        roomEventPublisher.publish(result.events());
    }

    private void validateMinimumPlayers(RoomState room) {
        if (room.memberCount() < 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "cannot_start_game");
        }
    }

    private List<RoomGameQuestion> selectQuestions(RoomState room, String randomSeed) {
        List<Question> questions = new ArrayList<>(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(
                room.mapId(),
                QuestionStatus.ACTIVE
        ));
        if (questions.size() < room.selectedQuestionCount()) {
            throw new BusinessException(HttpStatus.CONFLICT, "cannot_start_game");
        }

        Collections.shuffle(questions, new java.util.Random(randomSeed.hashCode()));
        List<Question> selectedQuestions = questions.stream()
                .limit(room.selectedQuestionCount())
                .toList();
        List<Long> questionIds = selectedQuestions.stream()
                .map(Question::getId)
                .toList();
        Map<Long, List<QuestionAnswer>> answersByQuestionId = questionAnswerRepository
                .findByQuestionIdInOrderByQuestionIdAscIdAsc(questionIds)
                .stream()
                .collect(Collectors.groupingBy(answer -> answer.getQuestion().getId()));
        Map<Long, QuestionMedia> mediaByQuestionId = questionMediaRepository.findByQuestionIdIn(questionIds)
                .stream()
                .collect(Collectors.toMap(media -> media.getQuestion().getId(), Function.identity()));

        return java.util.stream.IntStream.range(0, selectedQuestions.size())
                .mapToObj(index -> toGameQuestion(
                        selectedQuestions.get(index),
                        index + 1,
                        answersByQuestionId.getOrDefault(selectedQuestions.get(index).getId(), List.of()),
                        mediaByQuestionId.get(selectedQuestions.get(index).getId())
                ))
                .toList();
    }

    private RoomGameQuestion toGameQuestion(
            Question question,
            int questionNumber,
            List<QuestionAnswer> answers,
            QuestionMedia media
    ) {
        List<QuestionAnswer> sortedAnswers = answers.stream()
                .sorted(Comparator.comparing(QuestionAnswer::isPrimary).reversed()
                        .thenComparing(QuestionAnswer::getId))
                .toList();
        String primaryAnswer = sortedAnswers.stream()
                .filter(QuestionAnswer::isPrimary)
                .findFirst()
                .or(() -> sortedAnswers.stream().findFirst())
                .map(QuestionAnswer::getAnswerText)
                .orElse(null);
        String mediaUrl = mediaUrl(media);

        return new RoomGameQuestion(
                question.getId(),
                questionNumber,
                question.getPromptText(),
                sortedAnswers.stream()
                        .map(QuestionAnswer::getAnswerKey)
                        .toList(),
                primaryAnswer,
                mediaUrl,
                media == null ? null : media.getSourceType().name(),
                media == null ? null : media.getStartTimeMs(),
                media == null ? null : media.getEndTimeMs(),
                media == null ? null : media.getDurationMs()
        );
    }

    private String mediaUrl(QuestionMedia media) {
        if (media == null) {
            return null;
        }

        Asset asset = media.getAsset();
        if (asset != null) {
            return asset.getUrl();
        }

        return media.getSourceUrl();
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
