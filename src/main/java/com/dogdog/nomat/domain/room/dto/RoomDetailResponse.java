package com.dogdog.nomat.domain.room.dto;

import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import java.time.LocalDateTime;
import java.util.List;

public record RoomDetailResponse(
        Long roomId,
        String title,
        String status,
        boolean hasPassword,
        int maxPlayers,
        int selectedQuestionCount,
        int answerTimeLimitSeconds,
        String timeLimitMode,
        boolean audioRepeatEnabled,
        boolean initialHintEnabled,
        int initialHintTriggerSeconds,
        MapResponse map,
        Long hostUserId,
        int memberCount,
        List<MemberResponse> members,
        LocalDateTime createdAt
) {

    public static RoomDetailResponse from(RoomState room) {
        return new RoomDetailResponse(
                room.roomId(),
                room.title(),
                room.status().name(),
                room.hasPassword(),
                room.maxPlayers(),
                room.selectedQuestionCount(),
                room.answerTimeLimitSeconds(),
                room.timeLimitMode().name(),
                room.audioRepeatEnabled(),
                room.initialHintEnabled(),
                room.initialHintTriggerSeconds(),
                MapResponse.from(room),
                room.hostUserId(),
                room.memberCount(),
                room.members().stream()
                        .map(MemberResponse::from)
                        .toList(),
                room.createdAt()
        );
    }

    public record MapResponse(
            Long mapId,
            String title,
            String questionType,
            Long categoryId,
            String categoryName,
            String thumbnailUrl,
            int questionCount
        ) {

        private static MapResponse from(RoomState room) {
            return new MapResponse(
                    room.mapId(),
                    room.mapTitle(),
                    room.questionType(),
                    room.categoryId(),
                    room.categoryName(),
                    room.mapThumbnailUrl(),
                    room.mapQuestionCount()
            );
        }
    }

    public record MemberResponse(
            Long userId,
            String nickname,
            String profileImageUrl,
            boolean host
    ) {

        private static MemberResponse from(RoomMember member) {
            return new MemberResponse(
                    member.userId(),
                    member.nickname(),
                    member.profileImageUrl(),
                    member.host()
            );
        }
    }
}
