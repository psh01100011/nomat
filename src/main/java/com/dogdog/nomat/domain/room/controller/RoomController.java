package com.dogdog.nomat.domain.room.controller;

import com.dogdog.nomat.domain.auth.model.AuthenticatedUser;
import com.dogdog.nomat.domain.room.dto.CreateRoomRequest;
import com.dogdog.nomat.domain.room.dto.CreateRoomResponse;
import com.dogdog.nomat.domain.room.dto.CurrentRoomResponse;
import com.dogdog.nomat.domain.room.dto.JoinRoomRequest;
import com.dogdog.nomat.domain.room.dto.ModifyRoomSettingsRequest;
import com.dogdog.nomat.domain.room.dto.RoomDetailResponse;
import com.dogdog.nomat.domain.room.dto.RoomGameSnapshotResponse;
import com.dogdog.nomat.domain.room.dto.RoomInviteResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
        return ApiResponse.of("success_create_room", roomService.createRoom(AuthenticatedUser.from(jwt), request));
    }

    @GetMapping
    public ApiResponse<RoomListResponse> getRooms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long mapId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Boolean hasPassword,
            @RequestParam(defaultValue = "false") boolean excludePasswordRooms,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "true") boolean joinableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort
    ) {
        return ApiResponse.of(
                "success_get_rooms",
                roomService.getRooms(
                        keyword,
                        mapId,
                        categoryId,
                        questionType,
                        hasPassword,
                        excludePasswordRooms,
                        status,
                        joinableOnly,
                        page,
                        size,
                        sort
                )
        );
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomDetailResponse> getRoom(@PathVariable Long roomId) {
        return ApiResponse.of("success_get_room", roomService.getRoom(roomId));
    }

    @GetMapping("/me/current")
    public ApiResponse<CurrentRoomResponse> getCurrentRoom(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of("success_get_current_room", roomService.getCurrentRoom(AuthenticatedUser.from(jwt)));
    }

    @PostMapping("/{roomId}/join")
    public ApiResponse<RoomDetailResponse> joinRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId,
            @Valid @RequestBody(required = false) JoinRoomRequest request
    ) {
        return ApiResponse.of("success_join_room", roomService.joinRoom(AuthenticatedUser.from(jwt), roomId, request));
    }

    @PostMapping("/{roomId}/invite")
    public ApiResponse<RoomInviteResponse> createRoomInvite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId
    ) {
        return ApiResponse.of(
                "success_create_room_invite",
                roomService.createRoomInvite(AuthenticatedUser.from(jwt), roomId)
        );
    }

    @PostMapping("/invites/{inviteToken}/join")
    public ApiResponse<RoomDetailResponse> joinRoomByInvite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String inviteToken
    ) {
        return ApiResponse.of(
                "success_join_room",
                roomService.joinRoomByInvite(AuthenticatedUser.from(jwt), inviteToken)
        );
    }

    @PostMapping("/{roomId}/leave")
    public ApiResponse<Void> leaveRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId
    ) {
        roomService.leaveRoom(AuthenticatedUser.from(jwt), roomId);
        return ApiResponse.success("success_leave_room");
    }

    @PatchMapping("/{roomId}/settings")
    public ApiResponse<RoomDetailResponse> modifyRoomSettings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId,
            @Valid @RequestBody ModifyRoomSettingsRequest request
    ) {
        return ApiResponse.of(
                "success_modify_room_settings",
                roomService.modifyRoomSettings(AuthenticatedUser.from(jwt), roomId, request)
        );
    }

    @DeleteMapping("/{roomId}")
    public ApiResponse<Void> closeRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId
    ) {
        roomService.closeRoom(AuthenticatedUser.from(jwt), roomId);
        return ApiResponse.success("success_delete_room");
    }

    @DeleteMapping("/{roomId}/members/{targetUserId}")
    public ApiResponse<Void> kickRoomMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId,
            @PathVariable Long targetUserId
    ) {
        roomService.kickRoomMember(AuthenticatedUser.from(jwt), roomId, targetUserId);
        return ApiResponse.success("success_kick_room_member");
    }

    @PostMapping("/{roomId}/start")
    public ApiResponse<RoomDetailResponse> startGame(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId
    ) {
        return ApiResponse.of("success_start_game", roomService.startGame(AuthenticatedUser.from(jwt), roomId));
    }

    @GetMapping("/{roomId}/game/snapshot")
    public ApiResponse<RoomGameSnapshotResponse> getGameSnapshot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roomId
    ) {
        return ApiResponse.of(
                "success_get_game_snapshot",
                roomService.getGameSnapshot(AuthenticatedUser.from(jwt), roomId)
        );
    }
}
