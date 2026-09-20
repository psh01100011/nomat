package com.dogdog.nomat.domain.room.repository;

import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import java.time.Instant;
import java.util.ArrayList;
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
    private static final DefaultRedisScript<List> CLAIM_DUE_SCRIPT = new DefaultRedisScript<>("""
            local timers = redis.call(
                'ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1],
                'WITHSCORES', 'LIMIT', 0, ARGV[2]
            )
            for index = 1, #timers, 2 do
                local member = timers[index]
                redis.call('ZREM', KEYS[1], member)
                local roomId = string.match(member, '^([^:]+):')
                if roomId then
                    redis.call('SREM', ARGV[3] .. roomId, member)
                end
            end
            return timers
            """, List.class);

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

    public List<RoomGameTimer> claimDue(Instant now, int limit) {
        List<?> claimedValues = redisTemplate.execute(
                CLAIM_DUE_SCRIPT,
                List.of(TIMER_INDEX_KEY),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(limit),
                ROOM_TIMER_KEY_PREFIX
        );
        if (claimedValues == null || claimedValues.isEmpty()) {
            return List.of();
        }

        List<RoomGameTimer> timers = new ArrayList<>(claimedValues.size() / 2);
        for (int index = 0; index < claimedValues.size(); index += 2) {
            timers.add(parseTimer(
                    claimedValues.get(index).toString(),
                    claimedValues.get(index + 1).toString()
            ));
        }
        return List.copyOf(timers);
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

    private RoomGameTimer parseTimer(String memberKey, String score) {
        String[] parts = memberKey.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalStateException("Invalid room game timer key: " + memberKey);
        }
        return new RoomGameTimer(
                Long.valueOf(parts[0]),
                Integer.parseInt(parts[1]),
                RoomGameTimerType.valueOf(parts[2]),
                Instant.ofEpochMilli((long) Double.parseDouble(score))
        );
    }
}
