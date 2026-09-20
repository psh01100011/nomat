package com.dogdog.nomat.domain.room.repository;

import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.ClaimedRoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RoomGameTimerRepository {

    private static final String TIMER_INDEX_KEY = "rooms:game-timers:due-at";
    private static final String TIMER_LEASE_INDEX_KEY = "rooms:game-timers:lease-until";
    private static final String TIMER_LEASE_OWNER_KEY = "rooms:game-timers:lease-owner";
    private static final String TIMER_LEASE_DUE_AT_KEY = "rooms:game-timers:lease-due-at";
    private static final String ROOM_TIMER_KEY_PREFIX = "rooms:game-timers:room:";
    private static final DefaultRedisScript<Long> SCHEDULE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
            redis.call('ZREM', KEYS[2], ARGV[2])
            redis.call('HDEL', KEYS[3], ARGV[2])
            redis.call('HDEL', KEYS[4], ARGV[2])
            redis.call('SADD', KEYS[5], ARGV[2])
            redis.call('PEXPIRE', KEYS[5], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_IF_MATCH_SCRIPT = new DefaultRedisScript<>("""
            local removed = 0
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if score and tonumber(score) == tonumber(ARGV[2]) then
                removed = removed + redis.call('ZREM', KEYS[1], ARGV[1])
            end
            local leasedDueAt = redis.call('HGET', KEYS[4], ARGV[1])
            if leasedDueAt and tonumber(leasedDueAt) == tonumber(ARGV[2]) then
                removed = removed + redis.call('ZREM', KEYS[2], ARGV[1])
                redis.call('HDEL', KEYS[3], ARGV[1])
                redis.call('HDEL', KEYS[4], ARGV[1])
            end
            if removed > 0 then
                redis.call('SREM', KEYS[5], ARGV[1])
            end
            return removed
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = new DefaultRedisScript<>("""
            local removed = redis.call('ZREM', KEYS[1], ARGV[1])
            removed = removed + redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('HDEL', KEYS[3], ARGV[1])
            redis.call('HDEL', KEYS[4], ARGV[1])
            redis.call('SREM', KEYS[5], ARGV[1])
            return removed
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_ROOM_SCRIPT = new DefaultRedisScript<>("""
            local members = redis.call('SMEMBERS', KEYS[5])
            if #members > 0 then
                redis.call('ZREM', KEYS[1], unpack(members))
                redis.call('ZREM', KEYS[2], unpack(members))
                redis.call('HDEL', KEYS[3], unpack(members))
                redis.call('HDEL', KEYS[4], unpack(members))
            end
            redis.call('DEL', KEYS[5])
            return #members
            """, Long.class);
    private static final DefaultRedisScript<List> CLAIM_DUE_SCRIPT = new DefaultRedisScript<>("""
            local expired = redis.call(
                'ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1],
                'LIMIT', 0, ARGV[3]
            )
            for _, member in ipairs(expired) do
                local dueAt = redis.call('HGET', KEYS[4], member) or ARGV[1]
                redis.call('ZREM', KEYS[2], member)
                redis.call('HDEL', KEYS[3], member)
                redis.call('HDEL', KEYS[4], member)
                redis.call('ZADD', KEYS[1], dueAt, member)
            end

            local timers = redis.call(
                'ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1],
                'WITHSCORES', 'LIMIT', 0, ARGV[3]
            )
            for index = 1, #timers, 2 do
                local member = timers[index]
                local dueAt = timers[index + 1]
                redis.call('ZREM', KEYS[1], member)
                redis.call('ZADD', KEYS[2], ARGV[2], member)
                redis.call('HSET', KEYS[3], member, ARGV[4])
                redis.call('HSET', KEYS[4], member, dueAt)
            end
            return timers
            """, List.class);
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[2], ARGV[1]) ~= ARGV[2] then
                return 0
            end
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('HDEL', KEYS[2], ARGV[1])
            redis.call('HDEL', KEYS[3], ARGV[1])
            redis.call('SREM', KEYS[4], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RoomLifecycleProperties lifecycleProperties;

    public void schedule(RoomGameTimer timer) {
        redisTemplate.execute(
                SCHEDULE_SCRIPT,
                List.of(
                        TIMER_INDEX_KEY,
                        TIMER_LEASE_INDEX_KEY,
                        TIMER_LEASE_OWNER_KEY,
                        TIMER_LEASE_DUE_AT_KEY,
                        roomTimerKey(timer.roomId())
                ),
                String.valueOf(timer.dueAt().toEpochMilli()),
                timer.memberKey(),
                String.valueOf(lifecycleProperties.inactivityTtl().toMillis())
        );
    }

    public void remove(RoomGameTimer timer) {
        redisTemplate.execute(
                REMOVE_IF_MATCH_SCRIPT,
                timerKeys(timer.roomId()),
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
                timerKeys(roomId)
        );
    }

    public List<ClaimedRoomGameTimer> claimDue(Instant now, Duration leaseDuration, int limit) {
        String ownerToken = UUID.randomUUID().toString();
        List<?> claimedValues = redisTemplate.execute(
                CLAIM_DUE_SCRIPT,
                List.of(
                        TIMER_INDEX_KEY,
                        TIMER_LEASE_INDEX_KEY,
                        TIMER_LEASE_OWNER_KEY,
                        TIMER_LEASE_DUE_AT_KEY
                ),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(now.plus(leaseDuration).toEpochMilli()),
                String.valueOf(limit),
                ownerToken
        );
        if (claimedValues == null || claimedValues.isEmpty()) {
            return List.of();
        }

        List<ClaimedRoomGameTimer> timers = new ArrayList<>(claimedValues.size() / 2);
        for (int index = 0; index < claimedValues.size(); index += 2) {
            timers.add(new ClaimedRoomGameTimer(
                    parseTimer(
                            claimedValues.get(index).toString(),
                            claimedValues.get(index + 1).toString()
                    ),
                    ownerToken
            ));
        }
        return List.copyOf(timers);
    }

    public boolean complete(ClaimedRoomGameTimer claimedTimer) {
        RoomGameTimer timer = claimedTimer.timer();
        Long completed = redisTemplate.execute(
                COMPLETE_SCRIPT,
                List.of(
                        TIMER_LEASE_INDEX_KEY,
                        TIMER_LEASE_OWNER_KEY,
                        TIMER_LEASE_DUE_AT_KEY,
                        roomTimerKey(timer.roomId())
                ),
                timer.memberKey(),
                claimedTimer.ownerToken()
        );
        return Long.valueOf(1L).equals(completed);
    }

    private String roomTimerKey(Long roomId) {
        return ROOM_TIMER_KEY_PREFIX + roomId;
    }

    private void remove(Long roomId, int questionIndex, RoomGameTimerType type) {
        RoomGameTimer timer = new RoomGameTimer(roomId, questionIndex, type, Instant.EPOCH);
        redisTemplate.execute(
                REMOVE_SCRIPT,
                timerKeys(roomId),
                timer.memberKey()
        );
    }

    private List<String> timerKeys(Long roomId) {
        return List.of(
                TIMER_INDEX_KEY,
                TIMER_LEASE_INDEX_KEY,
                TIMER_LEASE_OWNER_KEY,
                TIMER_LEASE_DUE_AT_KEY,
                roomTimerKey(roomId)
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
