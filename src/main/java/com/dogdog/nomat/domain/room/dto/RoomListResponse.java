package com.dogdog.nomat.domain.room.dto;

import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import java.time.LocalDateTime;
import java.util.List;

public record RoomListResponse(
        List<RoomSummaryResponse> rooms,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static RoomListResponse of(List<RoomState> rooms, int page, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new RoomListResponse(
                rooms.stream()
                        .map(RoomSummaryResponse::from)
                        .toList(),
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages
        );
    }

    public record RoomSummaryResponse(
            Long roomId,
            String title,
            Long mapId,
            String mapTitle,
            String status,
            boolean hasPassword,
            int memberCount,
            int maxPlayers,
            String hostNickname,
            LocalDateTime createdAt
    ) {

        private static RoomSummaryResponse from(RoomState room) {
            String hostNickname = room.findMember(room.hostUserId())
                    .map(RoomMember::nickname)
                    .orElse(null);

            return new RoomSummaryResponse(
                    room.roomId(),
                    room.title(),
                    room.mapId(),
                    room.mapTitle(),
                    room.status().name(),
                    room.hasPassword(),
                    room.memberCount(),
                    room.maxPlayers(),
                    hostNickname,
                    room.createdAt()
            );
        }
    }
}
