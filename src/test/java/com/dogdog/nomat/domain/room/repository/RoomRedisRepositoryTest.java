package com.dogdog.nomat.domain.room.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RoomRedisRepositoryTest {

    private static final LocalDateTime TEST_NOW = LocalDateTime.now();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RoomLifecycleProperties lifecycleProperties;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private RoomRedisRepository roomRedisRepository;

    @BeforeEach
    void setUp() {
        lenient().when(lifecycleProperties.inactivityTtl()).thenReturn(Duration.ofMinutes(15));
    }

    @Test
    void tryRecordCorrectAnswerSavesWinnerWhenLockIsAcquired() throws Exception {
        RoomGameState gameState = gameState();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("rooms:games:25:question-resolution-lock:0"),
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
        verify(valueOperations).set("rooms:games:25", "saved-game-state", Duration.ofMinutes(15));
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rooms:games:25:question-resolution-lock:0")),
                anyString()
        );
    }

    @Test
    void saveGameStateRefreshesGameStateTtl() throws Exception {
        RoomGameState gameState = gameState();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(objectMapper.writeValueAsString(gameState)).willReturn("saved-game-state");

        roomRedisRepository.saveGameState(gameState);

        verify(valueOperations).set("rooms:games:25", "saved-game-state", Duration.ofMinutes(15));
    }

    @Test
    void getOrCreateInviteTokenReusesTokenWithRoomRemainingTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.getExpire("rooms:25", TimeUnit.SECONDS)).willReturn(600L);
        given(valueOperations.get("rooms:invites:25")).willReturn("0123456789abcdef0123456789abcdef");

        Optional<String> result = roomRedisRepository.getOrCreateInviteToken(25L);

        assertThat(result).contains("0123456789abcdef0123456789abcdef");
        verify(valueOperations).set(
                "rooms:invite-tokens:0123456789abcdef0123456789abcdef",
                "25",
                Duration.ofMinutes(10)
        );
    }

    @Test
    void tryRecordCorrectAnswerReturnsEmptyWhenLockIsAlreadyHeld() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("rooms:games:25:question-resolution-lock:0"),
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
                eq("rooms:games:25:question-resolution-lock:0"),
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
                eq(List.of("rooms:games:25:question-resolution-lock:0")),
                anyString()
        );
    }

    @Test
    void tryEndQuestionForAudioLoadFailureSavesFailureWhenLockIsAcquired() throws Exception {
        RoomGameState gameState = audioGameState();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("rooms:games:25:question-resolution-lock:0"),
                anyString(),
                eq(Duration.ofSeconds(3))
        )).willReturn(true);
        given(valueOperations.get("rooms:games:25")).willReturn("game-state");
        given(objectMapper.readValue("game-state", RoomGameState.class)).willReturn(gameState);
        given(objectMapper.writeValueAsString(ArgumentMatchers.any(RoomGameState.class))).willReturn("saved-game-state");

        Optional<RoomGameState> result = roomRedisRepository.tryEndQuestionForAudioLoadFailure(25L, 0, 1L);

        assertThat(result).isPresent();
        assertThat(result.get().hasCurrentQuestionEnded()).isTrue();
        assertThat(result.get().questionOutcomes().get(1).endedReason()).isEqualTo("AUDIO_LOAD_FAILED");
        verify(valueOperations).set("rooms:games:25", "saved-game-state", Duration.ofMinutes(15));
    }

    @Test
    void refreshRoomActivityRefreshesAllRelatedKeys() {
        RoomState room = mock(RoomState.class);
        RoomMember member = mockMember(3L);
        RoomMember guest = mockMember(-2L);
        given(room.roomId()).willReturn(25L);
        given(room.members()).willReturn(List.of(member, guest));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("rooms:invites:25")).willReturn("invite-token");

        roomRedisRepository.refreshRoomActivity(room);

        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(
                        "rooms:index:expires-at",
                        "rooms:25",
                        "rooms:games:25",
                        "rooms:invites:25",
                        "users:rooms:3",
                        "users:rooms:-2",
                        "rooms:invite-tokens:invite-token"
                )),
                eq("900000"),
                eq("25")
        );
    }

    @Test
    void findRoomsRemovesExpiredRoomIndexesBeforeReadingList() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRange("rooms:index:created-at", 0, -1)).willReturn(Set.of());

        assertThat(roomRedisRepository.findRooms("latest")).isEmpty();

        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(
                        "rooms:index:expires-at",
                        "rooms:index:created-at",
                        "rooms:index:member-count"
                ))
        );
    }

    @Test
    void deleteRoomRemovesLifecycleIndexesAndMemberConnections() {
        RoomState room = mock(RoomState.class);
        RoomMember member = mockMember(3L);
        RoomMember guest = mockMember(-2L);
        given(room.roomId()).willReturn(25L);
        given(room.members()).willReturn(List.of(member, guest));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(valueOperations.get("rooms:invites:25")).willReturn("invite-token");

        roomRedisRepository.deleteRoom(room);

        verify(redisTemplate).delete("rooms:25");
        verify(redisTemplate).delete("rooms:games:25");
        verify(redisTemplate).delete("rooms:invites:25");
        verify(redisTemplate).delete("rooms:invite-tokens:invite-token");
        verify(redisTemplate).delete("users:rooms:3");
        verify(redisTemplate).delete("users:rooms:-2");
        verify(zSetOperations).remove("rooms:index:created-at", "25");
        verify(zSetOperations).remove("rooms:index:member-count", "25");
        verify(zSetOperations).remove("rooms:index:expires-at", "25");
    }

    @Test
    void tryEndQuestionForAudioLoadFailureDoesNotOverwriteEndedQuestion() throws Exception {
        RoomGameState gameState = audioGameState().withAudioLoadFailed(now());
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("rooms:games:25:question-resolution-lock:0"),
                anyString(),
                eq(Duration.ofSeconds(3))
        )).willReturn(true);
        given(valueOperations.get("rooms:games:25")).willReturn("game-state");
        given(objectMapper.readValue("game-state", RoomGameState.class)).willReturn(gameState);

        Optional<RoomGameState> result = roomRedisRepository.tryEndQuestionForAudioLoadFailure(25L, 0, 1L);

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(
                eq("rooms:games:25"),
                anyString(),
                ArgumentMatchers.any(Duration.class)
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

    private RoomGameState audioGameState() {
        return new RoomGameState(
                25L,
                "seed",
                List.of(audioQuestion()),
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

    private RoomGameQuestion audioQuestion() {
        return new RoomGameQuestion(
                1L,
                1,
                "문제",
                List.of("정답"),
                "정답",
                "https://example.com/audio.mp3",
                "YOUTUBE",
                null,
                null,
                30_000
        );
    }

    private RoomMember mockMember(Long userId) {
        RoomMember member = mock(RoomMember.class);
        given(member.userId()).willReturn(userId);
        return member;
    }

    private LocalDateTime now() {
        return TEST_NOW;
    }
}
