package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.config.RoomGameTimerProperties;
import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomGameTimerExecutionGuard {

    private static final String EXECUTION_LOCK_PREFIX = "rooms:game-timers:execution-lock:";
    private static final String COMPLETED_PREFIX = "rooms:game-timers:completed:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RoomGameTimerProperties timerProperties;
    private final RoomLifecycleProperties lifecycleProperties;

    public ExecutionStatus execute(RoomGameTimer timer, Runnable action) {
        String executionId = executionId(timer);
        String completedKey = COMPLETED_PREFIX + executionId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(completedKey))) {
            return ExecutionStatus.ALREADY_COMPLETED;
        }

        String lockKey = EXECUTION_LOCK_PREFIX + executionId;
        String ownerToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                ownerToken,
                timerProperties.executionLockDuration()
        );
        if (!Boolean.TRUE.equals(acquired)) {
            return Boolean.TRUE.equals(redisTemplate.hasKey(completedKey))
                    ? ExecutionStatus.ALREADY_COMPLETED
                    : ExecutionStatus.BUSY;
        }

        try {
            action.run();
            redisTemplate.opsForValue().set(completedKey, "1", lifecycleProperties.inactivityTtl());
            return ExecutionStatus.EXECUTED;
        } finally {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey), ownerToken);
        }
    }

    private String executionId(RoomGameTimer timer) {
        return timer.memberKey() + ":" + timer.dueAt().toEpochMilli();
    }

    public enum ExecutionStatus {
        EXECUTED,
        ALREADY_COMPLETED,
        BUSY
    }
}
