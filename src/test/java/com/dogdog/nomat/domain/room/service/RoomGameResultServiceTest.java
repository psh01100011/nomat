package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.game.entity.GamePlayerResult;
import com.dogdog.nomat.domain.game.entity.GameQuestionEndedReason;
import com.dogdog.nomat.domain.game.entity.GameQuestionResult;
import com.dogdog.nomat.domain.game.entity.GameSession;
import com.dogdog.nomat.domain.game.entity.GameSessionEndedReason;
import com.dogdog.nomat.domain.game.entity.GameSessionStatus;
import com.dogdog.nomat.domain.game.entity.MapPlayHistory;
import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.game.repository.GamePlayerResultRepository;
import com.dogdog.nomat.domain.game.repository.GameQuestionResultRepository;
import com.dogdog.nomat.domain.game.repository.GameSessionRepository;
import com.dogdog.nomat.domain.game.repository.MapPlayHistoryRepository;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuestionRepository;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.room.model.RoomDomainEventType;
import com.dogdog.nomat.domain.room.model.RoomEndedReason;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameQuestionOutcome;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomGameResultServiceTest {

    @Mock
    private RoomRedisRepository roomRedisRepository;

    @Mock
    private QuizMapRepository quizMapRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private GameQuestionResultRepository gameQuestionResultRepository;

    @Mock
    private GamePlayerResultRepository gamePlayerResultRepository;

    @Mock
    private MapPlayHistoryRepository mapPlayHistoryRepository;

    @Spy
    private RoomStateMachine roomStateMachine = new RoomStateMachine();

    @Mock
    private RoomEventPublisher roomEventPublisher;

    @InjectMocks
    private RoomGameResultService roomGameResultService;

    @Test
    void endGameSavesResultsAndCleansRedis() {
        User host = user(3L);
        QuizMap map = map();
        Question question = question(map);
        RoomState room = room().started("seed", now());
        RoomGameState gameState = gameState();
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        given(quizMapRepository.findById(15L)).willReturn(Optional.of(map));
        given(gameSessionRepository.save(any(GameSession.class))).will(returnsFirstArg());
        given(questionRepository.findAllById(List.of(1L))).willReturn(List.of(question));
        given(userRepository.findAllById(List.of(3L))).willReturn(List.of(host));
        given(mapPlayHistoryRepository.findByUserIdAndMapId(3L, 15L)).willReturn(Optional.empty());

        roomGameResultService.endGame(25L, null, RoomEndedReason.COMPLETED);

        ArgumentCaptor<GameSession> sessionCaptor = ArgumentCaptor.forClass(GameSession.class);
        verify(gameSessionRepository).save(sessionCaptor.capture());
        GameSession session = sessionCaptor.getValue();
        assertThat(session.getStatus()).isEqualTo(GameSessionStatus.COMPLETED);
        assertThat(session.getEndedReason()).isEqualTo(GameSessionEndedReason.COMPLETED);
        assertThat(session.getPlayerCount()).isEqualTo(1);
        assertThat(session.getSelectedQuestionCount()).isEqualTo(1);

        ArgumentCaptor<List<GameQuestionResult>> questionResultsCaptor = ArgumentCaptor.forClass(List.class);
        verify(gameQuestionResultRepository).saveAll(questionResultsCaptor.capture());
        GameQuestionResult questionResult = questionResultsCaptor.getValue().getFirst();
        assertThat(questionResult.getQuestionNumber()).isEqualTo(1);
        assertThat(questionResult.getWinnerUser()).isEqualTo(host);
        assertThat(questionResult.getEarnedScore()).isEqualTo(100);
        assertThat(questionResult.getEndedReason()).isEqualTo(GameQuestionEndedReason.CORRECT_ANSWER);

        ArgumentCaptor<List<GamePlayerResult>> playerResultsCaptor = ArgumentCaptor.forClass(List.class);
        verify(gamePlayerResultRepository).saveAll(playerResultsCaptor.capture());
        GamePlayerResult playerResult = playerResultsCaptor.getValue().getFirst();
        assertThat(playerResult.getUser()).isEqualTo(host);
        assertThat(playerResult.getScore()).isEqualTo(100);
        assertThat(playerResult.getRank()).isEqualTo(1);
        assertThat(playerResult.getCorrectCount()).isEqualTo(1);

        verify(mapPlayHistoryRepository).save(any(MapPlayHistory.class));
        assertThat(map.getPlayCount()).isEqualTo(1);
        verify(roomRedisRepository).deleteRoom(org.mockito.ArgumentMatchers.argThat(deletedRoom ->
                deletedRoom.status().name().equals("ENDED")
        ));
        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.GAME_ENDED
        ));
    }

    private RoomState room() {
        return RoomState.waiting(
                25L,
                "방",
                15L,
                "맵",
                null,
                7L,
                "음악",
                1,
                3,
                false,
                null,
                10,
                1,
                30,
                TimeLimitMode.FIXED,
                true,
                true,
                10,
                new RoomMember(3L, "tester3", null, true, now()),
                now()
        );
    }

    private RoomGameState gameState() {
        return new RoomGameState(
                25L,
                "seed",
                List.of(new RoomGameQuestion(1L, 1, "문제", List.of("정답"), "정답", null, null, null, null, null)),
                0,
                now(),
                30,
                now().plusSeconds(30),
                now().plusSeconds(3),
                false,
                3L,
                "정답",
                Map.of(3L, 100),
                Map.of(1, new RoomGameQuestionOutcome(
                        1L,
                        1,
                        3L,
                        "정답",
                        100,
                        3000,
                        "CORRECT_ANSWER",
                        now(),
                        now().plusSeconds(3)
                )),
                now(),
                null
        );
    }

    private QuizMap map() {
        QuizMap map = QuizMap.publish(
                user(1L),
                category(),
                null,
                QuestionType.AUDIO,
                "맵",
                "설명",
                MapVisibility.PUBLIC,
                1
        );
        ReflectionTestUtils.setField(map, "id", 15L);
        return map;
    }

    private Question question(QuizMap map) {
        Question question = Question.create(map, 1, "문제");
        ReflectionTestUtils.setField(question, "id", 1L);
        return question;
    }

    private User user(Long id) {
        User user = User.create("user" + id, "encoded-password", "tester" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Category category() {
        Category category = Category.create("음악");
        ReflectionTestUtils.setField(category, "id", 7L);
        return category;
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 8, 21, 20, 0);
    }
}
