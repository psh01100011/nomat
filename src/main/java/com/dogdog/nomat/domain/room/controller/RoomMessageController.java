package com.dogdog.nomat.domain.room.controller;

import com.dogdog.nomat.domain.room.dto.RoomChatMessageRequest;
import com.dogdog.nomat.domain.room.dto.RoomSkipVoteRequest;
import com.dogdog.nomat.domain.room.service.RoomGameProgressService;
import com.dogdog.nomat.domain.room.service.RoomMessageService;
import com.dogdog.nomat.global.config.StompUserPrincipal;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class RoomMessageController {

    private final RoomMessageService roomMessageService;
    private final RoomGameProgressService roomGameProgressService;

    @MessageMapping("/rooms/{roomId}/messages")
    public void sendMessage(
            Principal principal,
            @DestinationVariable Long roomId,
            @Valid RoomChatMessageRequest request
    ) {
        roomMessageService.sendMessage(userId(principal), roomId, request);
    }

    @MessageMapping("/rooms/{roomId}/skip-votes")
    public void voteToSkip(
            Principal principal,
            @DestinationVariable Long roomId,
            @Valid RoomSkipVoteRequest request
    ) {
        roomGameProgressService.voteToSkip(userId(principal), roomId, request);
    }

    private Long userId(Principal principal) {
        if (principal instanceof StompUserPrincipal stompUserPrincipal) {
            return stompUserPrincipal.userId();
        }

        return Long.valueOf(principal.getName());
    }
}
