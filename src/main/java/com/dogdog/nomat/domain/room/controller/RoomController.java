package com.dogdog.nomat.domain.room.controller;

import com.dogdog.nomat.domain.room.dto.CreateRoomRequest;
import com.dogdog.nomat.domain.room.dto.CreateRoomResponse;
import com.dogdog.nomat.domain.room.dto.JoinRoomRequest;
import com.dogdog.nomat.domain.room.dto.RoomDetailResponse;
import com.dogdog.nomat.domain.room.dto.RoomListResponse;
import com.dogdog.nomat.domain.room.service.RoomService;
import com.dogdog.nomat.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateRoomResponse> createRoom(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        Long userId = getUserId(jwt);
        return ApiResponse.of("success_create_room", roomService.createRoom(userId, request));
    }

    @GetMapping
    public ApiResponse<RoomListResponse> getRooms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long mapId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "true") boolean joinableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort
    ) {
        return ApiResponse.of(
                "success_get_rooms",
                roomService.getRooms(keyword, mapId, categoryId, status, joinableOnly, page, size, sort)
        );
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomDetailResponse> getRoom(@PathVariable Long roomId) {
        return ApiResponse.of("success_get_room", roomService.getRoom(roomId));
    }

    @PostMapping("/{roomId}/join")
    public ApiResponse<RoomDetailResponse> joinRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId,
            @Valid @RequestBody(required = false) JoinRoomRequest request
    ) {
        Long userId = getUserId(jwt);
        return ApiResponse.of("success_join_room", roomService.joinRoom(userId, roomId, request));
    }

    @PostMapping("/{roomId}/leave")
    public ApiResponse<Void> leaveRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId
    ) {
        Long userId = getUserId(jwt);
        roomService.leaveRoom(userId, roomId);
        return ApiResponse.success("success_leave_room");
    }

    @DeleteMapping("/{roomId}")
    public ApiResponse<Void> closeRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId
    ) {
        Long userId = getUserId(jwt);
        roomService.closeRoom(userId, roomId);
        return ApiResponse.success("success_delete_room");
    }

    @DeleteMapping("/{roomId}/members/{targetUserId}")
    public ApiResponse<Void> kickRoomMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId,
            @PathVariable Long targetUserId
    ) {
        Long userId = getUserId(jwt);
        roomService.kickRoomMember(userId, roomId, targetUserId);
        return ApiResponse.success("success_kick_room_member");
    }

    private Long getUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return userId.longValue();
    }
}
