package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.comment.entity.MapCommentStatus;
import com.dogdog.nomat.domain.comment.repository.MapCommentRepository;
import com.dogdog.nomat.domain.game.repository.GameSessionRepository;
import com.dogdog.nomat.domain.map.dto.AdminMapCounterReconciliationResponse;
import com.dogdog.nomat.domain.map.dto.AdminMapCounterReconciliationResponse.CounterValues;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.MapFavoriteRepository;
import com.dogdog.nomat.domain.map.repository.MapLikeRepository;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMapCounterService {

    private final QuizMapRepository quizMapRepository;
    private final MapLikeRepository mapLikeRepository;
    private final MapFavoriteRepository mapFavoriteRepository;
    private final MapCommentRepository mapCommentRepository;
    private final GameSessionRepository gameSessionRepository;

    @Transactional(readOnly = true)
    public AdminMapCounterReconciliationResponse check(Long mapId) {
        QuizMap map = getMap(mapId);
        return AdminMapCounterReconciliationResponse.of(map, actualCounters(mapId), false);
    }

    @Transactional
    public AdminMapCounterReconciliationResponse reconcile(Long mapId) {
        QuizMap map = quizMapRepository.findByIdForUpdate(mapId)
                .orElseThrow(this::mapNotFound);
        CounterValues actual = actualCounters(mapId);
        CounterValues stored = storedCounters(map);
        boolean repaired = !stored.equals(actual);

        if (repaired) {
            quizMapRepository.replaceCounters(
                    mapId,
                    actual.likeCount(),
                    actual.favoriteCount(),
                    actual.commentCount(),
                    actual.playCount()
            );
            log.warn(
                    "event=map_counters_reconciled mapId={} stored={} actual={}",
                    mapId, stored, actual
            );
        }

        return new AdminMapCounterReconciliationResponse(
                mapId,
                stored,
                actual,
                true,
                repaired
        );
    }

    private QuizMap getMap(Long mapId) {
        return quizMapRepository.findById(mapId).orElseThrow(this::mapNotFound);
    }

    private CounterValues storedCounters(QuizMap map) {
        return new CounterValues(
                map.getLikeCount(),
                map.getFavoriteCount(),
                map.getCommentCount(),
                map.getPlayCount()
        );
    }

    private CounterValues actualCounters(Long mapId) {
        return new CounterValues(
                mapLikeRepository.countByMapId(mapId),
                mapFavoriteRepository.countByMapId(mapId),
                mapCommentRepository.countByMapIdAndStatus(mapId, MapCommentStatus.ACTIVE),
                gameSessionRepository.sumPlayerCountByMapId(mapId)
        );
    }

    private BusinessException mapNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "map_not_found");
    }
}
