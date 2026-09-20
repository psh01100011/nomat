package com.dogdog.nomat.domain.room.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.domain.auth.model.AuthenticatedUserType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RoomMemberTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void defaultsMissingUserTypeToMemberForExistingRedisData() throws Exception {
        String existingJson = """
                {
                  "userId": 3,
                  "nickname": "tester3",
                  "profileImageUrl": null,
                  "host": true,
                  "joinedAt": null
                }
                """;

        RoomMember member = objectMapper.readValue(existingJson, RoomMember.class);

        assertThat(member.userType()).isEqualTo(AuthenticatedUserType.MEMBER);
    }
}
