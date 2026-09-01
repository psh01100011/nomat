package com.dogdog.nomat.domain.room.repository;

import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomState;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class RoomRedisRepository {

    private static final String ROOM_ID_SEQUENCE_KEY = "rooms:sequence";
    private static final String ROOM_KEY_PREFIX = "rooms:";
    private static final String ROOM_GAME_KEY_PREFIX = "rooms:games:";
    private static final String ROOM_GAME_CORRECT_ANSWER_LOCK_SUFFIX = ":correct-answer-lock:";
    private static final String USER_ROOM_KEY_PREFIX = "users:rooms:";
    private static final String ROOM_CREATED_AT_INDEX_KEY = "rooms:index:created-at";
    private static final String ROOM_MEMBER_COUNT_INDEX_KEY = "rooms:index:member-count";
    private static final Duration ROOM_TTL = Duration.ofMinutes(30);
    private static final Duration CORRECT_ANSWER_LOCK_TTL = Duration.ofSeconds(3);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<Long> findJoinedRoomId(Long userId) {
        String roomId = redisTemplate.opsForValue().get(userRoomKey(userId));
        if (roomId == null) {
            return Optional.empty();
        }

        return Optional.of(Long.valueOf(roomId));
    }

    public Long nextRoomId() {
        return redisTemplate.opsForValue().increment(ROOM_ID_SEQUENCE_KEY);
    }

    public List<RoomState> findRooms(String sort) {
        String indexKey = "players".equals(sort) ? ROOM_MEMBER_COUNT_INDEX_KEY : ROOM_CREATED_AT_INDEX_KEY;
        Set<String> roomIds = redisTemplate.opsForZSet().reverseRange(indexKey, 0, -1);
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }

        return roomIds.stream()
                .map(Long::valueOf)
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<RoomState> findById(Long roomId) {
        String room = redisTemplate.opsForValue().get(roomKey(roomId));
        if (room == null) {
            return Optional.empty();
        }

        return Optional.of(deserialize(room));
    }

    public boolean createRoom(RoomState room) {
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(userRoomKey(room.hostUserId()), String.valueOf(room.roomId()), ROOM_TTL);
        if (!Boolean.TRUE.equals(reserved)) {
            return false;
        }

        redisTemplate.opsForValue().set(roomKey(room.roomId()), serialize(room), ROOM_TTL);
        redisTemplate.opsForZSet().add(
                ROOM_CREATED_AT_INDEX_KEY,
                String.valueOf(room.roomId()),
                room.createdAt().atZone(ZoneId.systemDefault()).toEpochSecond()
        );
        redisTemplate.opsForZSet().add(
                ROOM_MEMBER_COUNT_INDEX_KEY,
                String.valueOf(room.roomId()),
                room.memberCount()
        );
        return true;
    }

    public boolean saveJoinedRoom(RoomState room, Long joinedUserId) {
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(userRoomKey(joinedUserId), String.valueOf(room.roomId()), roomTtl(room.roomId()));
        if (!Boolean.TRUE.equals(reserved)) {
            return false;
        }

        try {
            saveRoom(room);
            return true;
        } catch (RuntimeException exception) {
            redisTemplate.delete(userRoomKey(joinedUserId));
            throw exception;
        }
    }

    public void saveRoom(RoomState room) {
        setWithRemainingTtl(roomKey(room.roomId()), serialize(room), ROOM_TTL);
        redisTemplate.opsForZSet().add(
                ROOM_MEMBER_COUNT_INDEX_KEY,
                String.valueOf(room.roomId()),
                room.memberCount()
        );
    }

    public void saveLeftRoom(RoomState room, Long leftUserId) {
        redisTemplate.delete(userRoomKey(leftUserId));
        if (room.status().isTerminal()) {
            deleteRoom(room);
            return;
        }

        saveRoom(room);
    }

    public void saveKickedRoom(RoomState room, Long targetUserId) {
        redisTemplate.delete(userRoomKey(targetUserId));
        saveRoom(room);
    }

    public void saveStartedRoom(RoomState room, RoomGameState gameState) {
        saveRoom(room);
        redisTemplate.opsForValue().set(roomGameKey(room.roomId()), serialize(gameState), roomTtl(room.roomId()));
    }

    public Optional<RoomGameState> findGameState(Long roomId) {
        String gameState = redisTemplate.opsForValue().get(roomGameKey(roomId));
        if (gameState == null) {
            return Optional.empty();
        }

        return Optional.of(deserialize(gameState, RoomGameState.class));
    }

    public void saveGameState(RoomGameState gameState) {
        setWithRemainingTtl(roomGameKey(gameState.roomId()), serialize(gameState), () -> roomTtl(gameState.roomId()));
    }

    public Optional<RoomGameState> tryRecordCorrectAnswer(
            Long roomId,
            int questionIndex,
            String answerKey,
            Long userId,
            String answer,
            int scoreToAdd
    ) {
        String lockKey = correctAnswerLockKey(roomId, questionIndex);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, CORRECT_ANSWER_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return Optional.empty();
        }

        try {
            RoomGameState gameState = findGameState(roomId).orElse(null);
            if (!canRecordCorrectAnswer(gameState, questionIndex, answerKey)) {
                return Optional.empty();
            }

            RoomGameState nextGameState = gameState.withCorrectAnswer(userId, answer, scoreToAdd);
            saveGameState(nextGameState);
            return Optional.of(nextGameState);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    public void deleteRoom(RoomState room) {
        redisTemplate.delete(roomKey(room.roomId()));
        redisTemplate.delete(roomGameKey(room.roomId()));
        redisTemplate.opsForZSet().remove(ROOM_CREATED_AT_INDEX_KEY, String.valueOf(room.roomId()));
        redisTemplate.opsForZSet().remove(ROOM_MEMBER_COUNT_INDEX_KEY, String.valueOf(room.roomId()));
        room.members().forEach(member -> redisTemplate.delete(userRoomKey(member.userId())));
    }

    private boolean canRecordCorrectAnswer(RoomGameState gameState, int questionIndex, String answerKey) {
        return gameState != null
                && gameState.hasCurrentQuestion()
                && gameState.currentQuestionIndex() == questionIndex
                && !gameState.hasCurrentQuestionWinner()
                && !gameState.hasCurrentQuestionEnded()
                && gameState.currentQuestion().answerKeys().contains(answerKey);
    }

    private void releaseLock(String lockKey, String lockValue) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockValue);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize Redis value.", exception);
        }
    }

    private RoomState deserialize(String room) {
        return deserialize(room, RoomState.class);
    }

    private void setWithRemainingTtl(String key, String value, Duration fallbackTtl) {
        setWithRemainingTtl(key, value, () -> fallbackTtl);
    }

    private void setWithRemainingTtl(String key, String value, Supplier<Duration> fallbackTtl) {
        redisTemplate.opsForValue().set(key, value, remainingTtl(key).orElseGet(fallbackTtl));
    }

    private Duration roomTtl(Long roomId) {
        return remainingTtl(roomKey(roomId)).orElse(ROOM_TTL);
    }

    private Optional<Duration> remainingTtl(String key) {
        Long seconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (seconds == null || seconds <= 0) {
            return Optional.empty();
        }

        return Optional.of(Duration.ofSeconds(seconds));
    }

    private <T> T deserialize(String value, Class<T> valueType) {
        try {
            return objectMapper.readValue(value, valueType);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize Redis value.", exception);
        }
    }

    private String roomKey(Long roomId) {
        return ROOM_KEY_PREFIX + roomId;
    }

    private String roomGameKey(Long roomId) {
        return ROOM_GAME_KEY_PREFIX + roomId;
    }

    private String correctAnswerLockKey(Long roomId, int questionIndex) {
        return roomGameKey(roomId) + ROOM_GAME_CORRECT_ANSWER_LOCK_SUFFIX + questionIndex;
    }

    private String userRoomKey(Long userId) {
        return USER_ROOM_KEY_PREFIX + userId;
    }
}
