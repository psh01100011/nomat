package com.dogdog.nomat.domain.room.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomGameStateTest {

    @Test
    void initialPlayerCountRemainsWhenMemberLeavesDuringGame() {
        RoomGameState state = RoomGameState.started(
                25L,
                "seed",
                List.of(),
                Map.of(3L, 0, -1L, 0),
                LocalDateTime.now()
        );

        RoomGameState afterGuestLeft = state.withoutMember(-1L);

        assertThat(afterGuestLeft.scores()).containsOnlyKeys(3L);
        assertThat(afterGuestLeft.initialPlayerCount()).isEqualTo(2);
    }
}
