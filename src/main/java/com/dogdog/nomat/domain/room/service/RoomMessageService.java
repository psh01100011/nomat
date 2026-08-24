package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.dto.RoomChatMessageRequest;
import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RoomMessageService {

    private static final int CORRECT_ANSWER_SCORE = 100;

    private final RoomRedisRepository roomRedisRepository;
    private final RoomEventPublisher roomEventPublisher;
    private final RoomGameProgressService roomGameProgressService;
    private final RoomMessageRateLimiter roomMessageRateLimiter;

    public void sendMessage(Long userId, Long roomId, RoomChatMessageRequest request) {
        RoomState room = roomRedisRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));
        RoomMember member = room.findMember(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "forbidden_room_access"));

        String content = normalizeContent(request.content());
        String clientMessageId = normalizeClientMessageId(request.clientMessageId());
        roomMessageRateLimiter.checkAllowed(roomId, userId, clientMessageId);
        LocalDateTime now = LocalDateTime.now();
        List<RoomDomainEvent> events = new ArrayList<>();
        events.add(RoomDomainEvent.chatMessage(roomId, userId, member.nickname(), content, clientMessageId, now));
        Integer endedQuestionIndex = null;

        if (room.status() == RoomStatus.PLAYING) {
            RoomGameState correctGameState = roomRedisRepository.findGameState(roomId)
                    .filter(gameState -> isCorrectAnswer(gameState, content))
                    .orElse(null);
            if (correctGameState != null) {
                endedQuestionIndex = recordCorrectAnswer(
                        correctGameState,
                        userId,
                        member.nickname(),
                        content,
                        answerKey(content),
                        now,
                        events
                );
            }
        }

        roomEventPublisher.publish(events);
        if (endedQuestionIndex != null) {
            roomGameProgressService.scheduleQuestionAdvance(roomId, endedQuestionIndex);
        }
    }

    private Integer recordCorrectAnswer(
            RoomGameState gameState,
            Long userId,
            String nickname,
            String content,
            String answerKey,
            LocalDateTime now,
            List<RoomDomainEvent> events
    ) {
        RoomGameState nextGameState = roomRedisRepository.tryRecordCorrectAnswer(
                        gameState.roomId(),
                        gameState.currentQuestionIndex(),
                        answerKey,
                        userId,
                        content,
                        CORRECT_ANSWER_SCORE
                )
                .orElse(null);
        if (nextGameState == null) {
            return null;
        }

        RoomGameQuestion question = nextGameState.currentQuestion();
        int score = nextGameState.scores().getOrDefault(userId, 0);

        events.add(RoomDomainEvent.correctAnswer(nextGameState.roomId(), userId, nickname, question.questionNumber(), now));
        events.add(RoomDomainEvent.scoreUpdated(nextGameState.roomId(), userId, nickname, score, now));
        events.add(RoomDomainEvent.questionEnded(nextGameState.roomId(), question.questionNumber(), now));
        return nextGameState.currentQuestionIndex();
    }

    private boolean isCorrectAnswer(RoomGameState gameState, String content) {
        if (!gameState.hasCurrentQuestion()
                || gameState.hasCurrentQuestionWinner()
                || gameState.hasCurrentQuestionEnded()) {
            return false;
        }

        String answerKey = answerKey(content);
        return gameState.currentQuestion().answerKeys().contains(answerKey);
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }

        String normalizedContent = content.trim();
        if (normalizedContent.length() > 300) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        return normalizedContent;
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (!StringUtils.hasText(clientMessageId)) {
            return null;
        }

        String normalizedClientMessageId = clientMessageId.trim();
        if (normalizedClientMessageId.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        return normalizedClientMessageId;
    }

    private String answerKey(String answer) {
        return answer.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
