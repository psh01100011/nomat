package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.map.entity.QuizMap;

public record AdminMapCounterReconciliationResponse(
        Long mapId,
        CounterValues stored,
        CounterValues actual,
        boolean consistent,
        boolean repaired
) {

    public static AdminMapCounterReconciliationResponse of(
            QuizMap map,
            CounterValues actual,
            boolean repaired
    ) {
        CounterValues stored = new CounterValues(
                map.getLikeCount(),
                map.getFavoriteCount(),
                map.getCommentCount(),
                map.getPlayCount()
        );
        return new AdminMapCounterReconciliationResponse(
                map.getId(),
                stored,
                actual,
                stored.equals(actual),
                repaired
        );
    }

    public record CounterValues(
            long likeCount,
            long favoriteCount,
            long commentCount,
            long playCount
    ) {
    }
}
