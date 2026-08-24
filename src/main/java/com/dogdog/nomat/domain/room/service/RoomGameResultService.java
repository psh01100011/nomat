package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.game.entity.GameQuestionEndedReason;
import com.dogdog.nomat.domain.game.entity.GameQuestionResult;
import com.dogdog.nomat.domain.game.entity.GamePlayerResult;
import com.dogdog.nomat.domain.game.entity.GameSession;
import com.dogdog.nomat.domain.game.entity.GameSessionEndedReason;
import com.dogdog.nomat.domain.game.entity.MapPlayHistory;
import com.dogdog.nomat.domain.game.repository.GamePlayerResultRepository;
import com.dogdog.nomat.domain.game.repository.GameQuestionResultRepository;
import com.dogdog.nomat.domain.game.repository.GameSessionRepository;
import com.dogdog.nomat.domain.game.repository.MapPlayHistoryRepository;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuestionRepository;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.room.model.RoomCommand;
import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import com.dogdog.nomat.domain.room.model.RoomEndedReason;
import com.dogdog.nomat.domain.room.model.RoomGameQuestionOutcome;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomTransitionResult;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomGameResultService {

    private final RoomRedisRepository roomRedisRepository;
    private final QuizMapRepository quizMapRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final GameSessionRepository gameSessionRepository;
    private final GameQuestionResultRepository gameQuestionResultRepository;
    private final GamePlayerResultRepository gamePlayerResultRepository;
    private final MapPlayHistoryRepository mapPlayHistoryRepository;
    private final RoomStateMachine roomStateMachine;
    private final RoomEventPublisher roomEventPublisher;

    @Transactional
    public void endGame(Long roomId, Long endedByUserId, RoomEndedReason reason) {
        RoomState room = roomRedisRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));
        RoomGameState gameState = roomRedisRepository.findGameState(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));
        QuizMap map = quizMapRepository.findById(room.mapId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "map_not_found"));
        User endedByUser = endedByUserId == null
                ? null
                : userRepository.findById(endedByUserId).orElse(null);

        LocalDateTime endedAt = LocalDateTime.now();
        RoomTransitionResult result = roomStateMachine.transition(
                room,
                RoomCommand.endGame(endedByUserId, endedAt)
        );

        GameSession session = gameSessionRepository.save(toGameSession(
                room,
                gameState,
                map,
                endedReason(reason),
                endedByUser,
                endedAt
        ));
        saveQuestionResults(session, gameState);
        savePlayerResultsAndHistories(session, room, gameState, map, endedAt);
        map.increasePlayCount();
        roomRedisRepository.deleteRoom(result.room());
        roomEventPublisher.publish(result.events().isEmpty()
                ? List.of(RoomDomainEvent.gameEnded(result.room(), reason, endedAt))
                : result.events());
    }

    private GameSession toGameSession(
            RoomState room,
            RoomGameState gameState,
            QuizMap map,
            GameSessionEndedReason endedReason,
            User endedByUser,
            LocalDateTime endedAt
    ) {
        return GameSession.complete(
                map,
                room.mapVersion(),
                room.title(),
                room.hasPassword(),
                room.maxPlayers(),
                room.memberCount(),
                room.selectedQuestionCount(),
                gameState.randomSeed(),
                room.answerTimeLimitSeconds(),
                room.timeLimitMode(),
                room.audioRepeatEnabled(),
                room.initialHintEnabled(),
                room.initialHintTriggerSeconds(),
                endedReason,
                endedByUser,
                gameState.startedAt(),
                endedAt
        );
    }

    private void saveQuestionResults(GameSession session, RoomGameState gameState) {
        List<RoomGameQuestionOutcome> outcomes = gameState.questionOutcomes().values().stream()
                .sorted(Comparator.comparing(RoomGameQuestionOutcome::questionNumber))
                .toList();
        if (outcomes.isEmpty()) {
            return;
        }

        Map<Long, Question> questionsById = questionRepository.findAllById(
                        outcomes.stream().map(RoomGameQuestionOutcome::questionId).toList()
                )
                .stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, User> usersById = userRepository.findAllById(
                        outcomes.stream()
                                .map(RoomGameQuestionOutcome::winnerUserId)
                                .filter(Objects::nonNull)
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<GameQuestionResult> results = outcomes.stream()
                .map(outcome -> GameQuestionResult.create(
                        session,
                        questionsById.get(outcome.questionId()),
                        outcome.questionNumber(),
                        usersById.get(outcome.winnerUserId()),
                        outcome.winnerAnswer(),
                        outcome.earnedScore(),
                        outcome.answeredMs(),
                        GameQuestionEndedReason.valueOf(outcome.endedReason()),
                        outcome.startedAt(),
                        outcome.endedAt()
                ))
                .toList();
        gameQuestionResultRepository.saveAll(results);
    }

    private void savePlayerResultsAndHistories(
            GameSession session,
            RoomState room,
            RoomGameState gameState,
            QuizMap map,
            LocalDateTime endedAt
    ) {
        List<Long> userIds = room.members().stream().map(RoomMember::userId).toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Long> correctCounts = gameState.questionOutcomes().values().stream()
                .map(RoomGameQuestionOutcome::winnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<PlayerScore> playerScores = userIds.stream()
                .map(userId -> new PlayerScore(
                        userId,
                        gameState.scores().getOrDefault(userId, 0),
                        correctCounts.getOrDefault(userId, 0L).intValue()
                ))
                .sorted(Comparator.comparing(PlayerScore::score).reversed()
                        .thenComparing(PlayerScore::userId))
                .toList();

        List<GamePlayerResult> playerResults = java.util.stream.IntStream.range(0, playerScores.size())
                .mapToObj(index -> {
                    PlayerScore playerScore = playerScores.get(index);
                    User user = usersById.get(playerScore.userId());
                    updatePlayHistory(user, map, playerScore.score(), endedAt);
                    return GamePlayerResult.create(
                            session,
                            user,
                            playerScore.score(),
                            index + 1,
                            playerScore.correctCount(),
                            endedAt
                    );
                })
                .toList();
        gamePlayerResultRepository.saveAll(playerResults);
    }

    private void updatePlayHistory(User user, QuizMap map, int score, LocalDateTime endedAt) {
        MapPlayHistory history = mapPlayHistoryRepository.findByUserIdAndMapId(user.getId(), map.getId())
                .orElseGet(() -> MapPlayHistory.create(user, map, score, endedAt));
        if (history.getId() != null) {
            history.recordPlay(score, endedAt);
        }
        mapPlayHistoryRepository.save(history);
    }

    private GameSessionEndedReason endedReason(RoomEndedReason reason) {
        return switch (reason) {
            case COMPLETED -> GameSessionEndedReason.COMPLETED;
            case HOST_ABORTED -> GameSessionEndedReason.HOST_ABORTED;
            case ALL_LEFT -> GameSessionEndedReason.ALL_LEFT;
            case SERVER_ERROR -> GameSessionEndedReason.SERVER_ERROR;
        };
    }

    private record PlayerScore(Long userId, int score, int correctCount) {
    }
}
