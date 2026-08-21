package com.dogdog.nomat.domain.room.repository;

import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomState;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class RoomRedisRepository {

    private static final String ROOM_ID_SEQUENCE_KEY = "rooms:sequence";
    private static final String ROOM_KEY_PREFIX = "rooms:";
    private static final String ROOM_GAME_KEY_PREFIX = "rooms:games:";
    private static final String USER_ROOM_KEY_PREFIX = "users:rooms:";
    private static final String ROOM_CREATED_AT_INDEX_KEY = "rooms:index:created-at";
    private static final String ROOM_MEMBER_COUNT_INDEX_KEY = "rooms:index:member-count";
    private static final Duration ROOM_TTL = Duration.ofHours(6);

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
                .setIfAbsent(userRoomKey(joinedUserId), String.valueOf(room.roomId()), ROOM_TTL);
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
        redisTemplate.opsForValue().set(roomKey(room.roomId()), serialize(room), ROOM_TTL);
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
        redisTemplate.opsForValue().set(roomGameKey(room.roomId()), serialize(gameState), ROOM_TTL);
    }

    public Optional<RoomGameState> findGameState(Long roomId) {
        String gameState = redisTemplate.opsForValue().get(roomGameKey(roomId));
        if (gameState == null) {
            return Optional.empty();
        }

        return Optional.of(deserialize(gameState, RoomGameState.class));
    }

    public void deleteRoom(RoomState room) {
        redisTemplate.delete(roomKey(room.roomId()));
        redisTemplate.delete(roomGameKey(room.roomId()));
        redisTemplate.opsForZSet().remove(ROOM_CREATED_AT_INDEX_KEY, String.valueOf(room.roomId()));
        redisTemplate.opsForZSet().remove(ROOM_MEMBER_COUNT_INDEX_KEY, String.valueOf(room.roomId()));
        room.members().forEach(member -> redisTemplate.delete(userRoomKey(member.userId())));
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

    private String userRoomKey(Long userId) {
        return USER_ROOM_KEY_PREFIX + userId;
    }
}
