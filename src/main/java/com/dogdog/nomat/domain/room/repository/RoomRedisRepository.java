package com.dogdog.nomat.domain.room.repository;

import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomState;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
    private static final String ROOM_INVITE_KEY_PREFIX = "rooms:invites:";
    private static final String ROOM_INVITE_TOKEN_KEY_PREFIX = "rooms:invite-tokens:";
    private static final String ROOM_GAME_QUESTION_RESOLUTION_LOCK_SUFFIX = ":question-resolution-lock:";
    private static final String USER_ROOM_KEY_PREFIX = "users:rooms:";
    private static final String ROOM_CREATED_AT_INDEX_KEY = "rooms:index:created-at";
    private static final String ROOM_MEMBER_COUNT_INDEX_KEY = "rooms:index:member-count";
    private static final String ROOM_EXPIRES_AT_INDEX_KEY = "rooms:index:expires-at";
    private static final Duration CORRECT_ANSWER_LOCK_TTL = Duration.ofSeconds(3);
    private static final DefaultRedisScript<Long> REFRESH_ACTIVITY_SCRIPT = new DefaultRedisScript<>("""
            local ttl = tonumber(ARGV[1])
            for index = 2, #KEYS do
                if redis.call('EXISTS', KEYS[index]) == 1 then
                    redis.call('PEXPIRE', KEYS[index], ttl)
                end
            end
            local time = redis.call('TIME')
            local expiresAt = (time[1] * 1000) + math.floor(time[2] / 1000) + ttl
            redis.call('ZADD', KEYS[1], expiresAt, ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_EXPIRED_INDEXES_SCRIPT = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = (time[1] * 1000) + math.floor(time[2] / 1000)
            local expiredRoomIds = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now)
            for _, roomId in ipairs(expiredRoomIds) do
                redis.call('ZREM', KEYS[1], roomId)
                redis.call('ZREM', KEYS[2], roomId)
                redis.call('ZREM', KEYS[3], roomId)
            end
            return #expiredRoomIds
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RoomLifecycleProperties lifecycleProperties;
    private final RoomGameTimerRepository roomGameTimerRepository;

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
        removeExpiredRoomIndexes();
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
        Duration roomTtl = roomTtl();
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(userRoomKey(room.hostUserId()), String.valueOf(room.roomId()), roomTtl);
        if (!Boolean.TRUE.equals(reserved)) {
            return false;
        }

        redisTemplate.opsForValue().set(roomKey(room.roomId()), serialize(room), roomTtl);
        getOrCreateInviteToken(room.roomId());
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
        refreshRoomActivity(room);
        return true;
    }

    public Optional<String> getOrCreateInviteToken(Long roomId) {
        Optional<Duration> remainingRoomTtl = remainingTtl(roomKey(roomId));
        if (remainingRoomTtl.isEmpty()) {
            return Optional.empty();
        }

        String inviteKey = roomInviteKey(roomId);
        String inviteToken = redisTemplate.opsForValue().get(inviteKey);
        if (inviteToken == null) {
            String candidate = UUID.randomUUID().toString().replace("-", "");
            Boolean created = redisTemplate.opsForValue().setIfAbsent(inviteKey, candidate, remainingRoomTtl.get());
            inviteToken = Boolean.TRUE.equals(created)
                    ? candidate
                    : redisTemplate.opsForValue().get(inviteKey);
        }
        if (inviteToken == null) {
            return Optional.empty();
        }

        redisTemplate.opsForValue().set(inviteTokenKey(inviteToken), String.valueOf(roomId), remainingRoomTtl.get());
        return Optional.of(inviteToken);
    }

    public Optional<Long> findRoomIdByInviteToken(String inviteToken) {
        String roomId = redisTemplate.opsForValue().get(inviteTokenKey(inviteToken));
        if (roomId == null) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(roomId));
    }

    public boolean saveJoinedRoom(RoomState room, Long joinedUserId) {
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(userRoomKey(joinedUserId), String.valueOf(room.roomId()), roomTtl());
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
        redisTemplate.opsForValue().set(roomKey(room.roomId()), serialize(room), roomTtl());
        redisTemplate.opsForZSet().add(
                ROOM_MEMBER_COUNT_INDEX_KEY,
                String.valueOf(room.roomId()),
                room.memberCount()
        );
        refreshRoomActivity(room);
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
        redisTemplate.opsForValue().set(roomGameKey(room.roomId()), serialize(gameState), roomTtl());
        refreshRoomActivity(room);
    }

    public Optional<RoomGameState> findGameState(Long roomId) {
        String gameState = redisTemplate.opsForValue().get(roomGameKey(roomId));
        if (gameState == null) {
            return Optional.empty();
        }

        return Optional.of(deserialize(gameState, RoomGameState.class));
    }

    public void saveGameState(RoomGameState gameState) {
        redisTemplate.opsForValue().set(roomGameKey(gameState.roomId()), serialize(gameState), roomTtl());
        findById(gameState.roomId()).ifPresent(this::refreshRoomActivity);
    }

    public void refreshRoomActivity(RoomState room) {
        Duration ttl = roomTtl();
        String inviteToken = redisTemplate.opsForValue().get(roomInviteKey(room.roomId()));
        List<String> keys = new ArrayList<>();
        keys.add(ROOM_EXPIRES_AT_INDEX_KEY);
        keys.add(roomKey(room.roomId()));
        keys.add(roomGameKey(room.roomId()));
        keys.add(roomInviteKey(room.roomId()));
        room.members().forEach(member -> keys.add(userRoomKey(member.userId())));
        if (inviteToken != null) {
            keys.add(inviteTokenKey(inviteToken));
        }

        redisTemplate.execute(
                REFRESH_ACTIVITY_SCRIPT,
                keys,
                String.valueOf(ttl.toMillis()),
                String.valueOf(room.roomId())
        );
    }

    public Optional<RoomGameState> tryRecordCorrectAnswer(
            Long roomId,
            int questionIndex,
            String answerKey,
            Long userId,
            String answer,
            int scoreToAdd
    ) {
        return withQuestionResolutionLock(roomId, questionIndex, gameState -> {
            if (!canRecordCorrectAnswer(gameState, questionIndex, answerKey)) {
                return null;
            }
            return gameState.withCorrectAnswer(userId, answer, scoreToAdd);
        });
    }

    public Optional<RoomGameState> tryEndQuestionForAudioLoadFailure(
            Long roomId,
            int questionIndex,
            Long questionId
    ) {
        return withQuestionResolutionLock(roomId, questionIndex, gameState -> {
            if (!canRecordAudioLoadFailure(gameState, questionIndex, questionId)) {
                return null;
            }
            return gameState.withAudioLoadFailed(java.time.LocalDateTime.now());
        });
    }

    private Optional<RoomGameState> withQuestionResolutionLock(
            Long roomId,
            int questionIndex,
            Function<RoomGameState, RoomGameState> transition
    ) {
        String lockKey = questionResolutionLockKey(roomId, questionIndex);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, CORRECT_ANSWER_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return Optional.empty();
        }

        try {
            RoomGameState gameState = findGameState(roomId).orElse(null);
            RoomGameState nextGameState = transition.apply(gameState);
            if (nextGameState == null) {
                return Optional.empty();
            }
            saveGameState(nextGameState);
            return Optional.of(nextGameState);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    public void deleteRoom(RoomState room) {
        roomGameTimerRepository.removeAll(room.roomId());
        String inviteToken = redisTemplate.opsForValue().get(roomInviteKey(room.roomId()));
        redisTemplate.delete(roomKey(room.roomId()));
        redisTemplate.delete(roomGameKey(room.roomId()));
        redisTemplate.delete(roomInviteKey(room.roomId()));
        if (inviteToken != null) {
            redisTemplate.delete(inviteTokenKey(inviteToken));
        }
        redisTemplate.opsForZSet().remove(ROOM_CREATED_AT_INDEX_KEY, String.valueOf(room.roomId()));
        redisTemplate.opsForZSet().remove(ROOM_MEMBER_COUNT_INDEX_KEY, String.valueOf(room.roomId()));
        redisTemplate.opsForZSet().remove(ROOM_EXPIRES_AT_INDEX_KEY, String.valueOf(room.roomId()));
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

    private boolean canRecordAudioLoadFailure(RoomGameState gameState, int questionIndex, Long questionId) {
        return gameState != null
                && gameState.hasCurrentQuestion()
                && gameState.currentQuestionIndex() == questionIndex
                && !gameState.hasCurrentQuestionWinner()
                && !gameState.hasCurrentQuestionEnded()
                && gameState.currentQuestion().questionId().equals(questionId)
                && gameState.currentQuestion().mediaUrl() != null;
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

    private Duration roomTtl() {
        return lifecycleProperties.inactivityTtl();
    }

    private Optional<Duration> remainingTtl(String key) {
        Long seconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (seconds == null || seconds <= 0) {
            return Optional.empty();
        }

        return Optional.of(Duration.ofSeconds(seconds));
    }

    private void removeExpiredRoomIndexes() {
        redisTemplate.execute(
                REMOVE_EXPIRED_INDEXES_SCRIPT,
                List.of(
                        ROOM_EXPIRES_AT_INDEX_KEY,
                        ROOM_CREATED_AT_INDEX_KEY,
                        ROOM_MEMBER_COUNT_INDEX_KEY
                )
        );
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

    private String roomInviteKey(Long roomId) {
        return ROOM_INVITE_KEY_PREFIX + roomId;
    }

    private String inviteTokenKey(String inviteToken) {
        return ROOM_INVITE_TOKEN_KEY_PREFIX + inviteToken;
    }

    private String questionResolutionLockKey(Long roomId, int questionIndex) {
        return roomGameKey(roomId) + ROOM_GAME_QUESTION_RESOLUTION_LOCK_SUFFIX + questionIndex;
    }

    private String userRoomKey(Long userId) {
        return USER_ROOM_KEY_PREFIX + userId;
    }
}
