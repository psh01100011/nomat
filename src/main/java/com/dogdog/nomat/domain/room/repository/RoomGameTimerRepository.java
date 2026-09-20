package com.dogdog.nomat.domain.room.repository;

import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RoomGameTimerRepository {

    private static final String TIMER_INDEX_KEY = "rooms:game-timers:due-at";
    private static final String ROOM_TIMER_KEY_PREFIX = "rooms:game-timers:room:";
    private static final DefaultRedisScript<Long> SCHEDULE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
            redis.call('SADD', KEYS[2], ARGV[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_IF_MATCH_SCRIPT = new DefaultRedisScript<>("""
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not score or tonumber(score) ~= tonumber(ARGV[2]) then
                return 0
            end
            local removed = redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            return removed
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = new DefaultRedisScript<>("""
            local removed = redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            return removed
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_ROOM_SCRIPT = new DefaultRedisScript<>("""
            local members = redis.call('SMEMBERS', KEYS[2])
            if #members > 0 then
                redis.call('ZREM', KEYS[1], unpack(members))
            end
            redis.call('DEL', KEYS[2])
            return #members
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RoomLifecycleProperties lifecycleProperties;

    public void schedule(RoomGameTimer timer) {
        redisTemplate.execute(
                SCHEDULE_SCRIPT,
                List.of(TIMER_INDEX_KEY, roomTimerKey(timer.roomId())),
                String.valueOf(timer.dueAt().toEpochMilli()),
                timer.memberKey(),
                String.valueOf(lifecycleProperties.inactivityTtl().toMillis())
        );
    }

    public void remove(RoomGameTimer timer) {
        redisTemplate.execute(
                REMOVE_IF_MATCH_SCRIPT,
                List.of(TIMER_INDEX_KEY, roomTimerKey(timer.roomId())),
                timer.memberKey(),
                String.valueOf(timer.dueAt().toEpochMilli())
        );
    }

    public void removeQuestionTimers(Long roomId, int questionIndex) {
        for (RoomGameTimerType type : List.of(
                RoomGameTimerType.HINT,
                RoomGameTimerType.QUESTION_TIMEOUT
        )) {
            remove(roomId, questionIndex, type);
        }
    }

    public void removeAll(Long roomId) {
        redisTemplate.execute(
                REMOVE_ROOM_SCRIPT,
                List.of(TIMER_INDEX_KEY, roomTimerKey(roomId))
        );
    }

    private String roomTimerKey(Long roomId) {
        return ROOM_TIMER_KEY_PREFIX + roomId;
    }

    private void remove(Long roomId, int questionIndex, RoomGameTimerType type) {
        RoomGameTimer timer = new RoomGameTimer(roomId, questionIndex, type, Instant.EPOCH);
        redisTemplate.execute(
                REMOVE_SCRIPT,
                List.of(TIMER_INDEX_KEY, roomTimerKey(roomId)),
                timer.memberKey()
        );
    }
}
