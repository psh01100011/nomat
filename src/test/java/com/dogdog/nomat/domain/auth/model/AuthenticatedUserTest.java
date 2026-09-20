package com.dogdog.nomat.domain.auth.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dogdog.nomat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

class AuthenticatedUserTest {

    @Test
    void requireMemberIdReturnsUserIdForMember() {
        AuthenticatedUser user = new AuthenticatedUser(1L, AuthenticatedUserType.MEMBER, null);

        assertThat(user.requireMemberId()).isEqualTo(1L);
    }

    @Test
    void requireMemberIdRejectsGuest() {
        AuthenticatedUser user = new AuthenticatedUser(-1L, AuthenticatedUserType.GUEST, "손님");

        assertThatThrownBy(user::requireMemberId)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("member_only");
    }

    @Test
    void requireGuestRejectsMember() {
        AuthenticatedUser user = new AuthenticatedUser(1L, AuthenticatedUserType.MEMBER, null);

        assertThatThrownBy(user::requireGuest)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("guest_only");
    }
}
