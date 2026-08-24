package com.dogdog.nomat.domain.room.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RoomRedisRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RoomRedisRepository roomRedisRepository;

    @Test
    void tryRecordCorrectAnswerSavesWinnerWhenLockIsAcquired() throws Exception {
        RoomGameState gameState = gameState();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("rooms:games:25:correct-answer-lock:0"),
                anyString(),
                eq(Duration.ofSeconds(3))
        )).willReturn(true);
        given(valueOperations.get("rooms:games:25")).willReturn("game-state");
        given(objectMapper.readValue("game-state", RoomGameState.class)).willReturn(gameState);
        given(objectMapper.writeValueAsString(ArgumentMatchers.any(RoomGameState.class))).willReturn("saved-game-state");

        Optional<RoomGameState> result = roomRedisRepository.tryRecordCorrectAnswer(
                25L,
                0,
                "정답",
                3L,
                "정 답",
                100
        );

        assertThat(result).isPresent();
        assertThat(result.get().currentQuestionWinnerUserId()).isEqualTo(3L);
        assertThat(result.get().currentQuestionWinnerAnswer()).isEqualTo("정 답");
        assertThat(result.get().scores()).containsEntry(3L, 100);
        verify(valueOperations).set("rooms:games:25", "saved-game-state", Duration.ofHours(6));
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rooms:games:25:correct-answer-lock:0")),
                anyString()
        );
    }

    @Test
    void tryRecordCorrectAnswerReturnsEmptyWhenLockIsAlreadyHeld() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("rooms:games:25:correct-answer-lock:0"),
                anyString(),
                eq(Duration.ofSeconds(3))
        )).willReturn(false);

        Optional<RoomGameState> result = roomRedisRepository.tryRecordCorrectAnswer(
                25L,
                0,
                "정답",
                3L,
                "정답",
                100
        );

        assertThat(result).isEmpty();
        verify(valueOperations, never()).get("rooms:games:25");
        verify(valueOperations, never()).set(
                eq("rooms:games:25"),
                anyString(),
                ArgumentMatchers.any(Duration.class)
        );
    }

    @Test
    void tryRecordCorrectAnswerDoesNotOverwriteExistingWinner() throws Exception {
        RoomGameState gameState = gameState().withCorrectAnswer(4L, "정답", 100);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("rooms:games:25:correct-answer-lock:0"),
                anyString(),
                eq(Duration.ofSeconds(3))
        )).willReturn(true);
        given(valueOperations.get("rooms:games:25")).willReturn("game-state");
        given(objectMapper.readValue("game-state", RoomGameState.class)).willReturn(gameState);

        Optional<RoomGameState> result = roomRedisRepository.tryRecordCorrectAnswer(
                25L,
                0,
                "정답",
                3L,
                "정답",
                100
        );

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(
                eq("rooms:games:25"),
                anyString(),
                ArgumentMatchers.any(Duration.class)
        );
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rooms:games:25:correct-answer-lock:0")),
                anyString()
        );
    }

    private RoomGameState gameState() {
        return new RoomGameState(
                25L,
                "seed",
                List.of(question()),
                0,
                now(),
                30,
                now().plusSeconds(30),
                null,
                false,
                null,
                null,
                Set.of(),
                Map.of(3L, 0, 4L, 0),
                Map.of(),
                now(),
                null
        );
    }

    private RoomGameQuestion question() {
        return new RoomGameQuestion(
                1L,
                1,
                "문제",
                List.of("정답"),
                "정답",
                null,
                null,
                null,
                null,
                null
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 8, 21, 20, 0);
    }
}
