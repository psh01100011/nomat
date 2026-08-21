package com.dogdog.nomat.domain.room.model;

import java.util.List;

public record RoomGameQuestion(
        Long questionId,
        int questionNumber,
        String promptText,
        List<String> answerKeys,
        String primaryAnswer,
        String mediaUrl,
        String mediaSourceType,
        Integer mediaStartTimeMs,
        Integer mediaEndTimeMs,
        Integer mediaDurationMs
) {

    public RoomGameQuestion {
        answerKeys = answerKeys == null ? List.of() : List.copyOf(answerKeys);
    }
}
