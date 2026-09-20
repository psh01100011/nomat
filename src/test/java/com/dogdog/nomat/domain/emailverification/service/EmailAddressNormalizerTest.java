package com.dogdog.nomat.domain.emailverification.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailAddressNormalizerTest {

    @Test
    void normalizesCaseAndAcceptsSyntacticallyValidAddress() {
        assertThat(EmailAddressNormalizer.normalizeNullable(" User.Name+tag@Gmail.COM "))
                .isEqualTo("user.name+tag@gmail.com");
        assertThat(EmailAddressNormalizer.isValid("user.name+tag@gmail.com")).isTrue();
        assertThat(EmailAddressNormalizer.isValid("user@a.c")).isTrue();
    }
}
