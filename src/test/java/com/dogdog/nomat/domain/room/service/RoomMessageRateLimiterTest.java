package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.dogdog.nomat.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RoomMessageRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RoomMessageRateLimiter roomMessageRateLimiter;

    @Test
    void checkAllowedPassesWhenTokenExists() {
        givenExecuteResult(0L);

        roomMessageRateLimiter.checkAllowed(25L, 3L, "message-1");
    }

    @Test
    void checkAllowedThrowsRateLimitedWhenTokenIsInsufficient() {
        givenExecuteResult(1L);

        assertThatThrownBy(() -> roomMessageRateLimiter.checkAllowed(25L, 3L, "message-1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(429);
                    assertThat(exception.getMessageCode()).isEqualTo("message_rate_limited");
                    assertThat(exception.getData())
                            .isEqualTo(new RoomMessageRateLimiter.RateLimitData(1, "message-1"));
                });
    }

    private void givenExecuteResult(Long result) {
        given(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rooms:message-rate:25:3")),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).willReturn(result);
    }
}
