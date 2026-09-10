package com.dogdog.nomat.domain.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuestLoginRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validGuestLoginRequestPassesValidation() {
        GuestLoginRequest request = new GuestLoginRequest("게스트_1");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void nicknameRequiresValidLengthAndFormat() {
        GuestLoginRequest tooShort = new GuestLoginRequest("a");
        GuestLoginRequest tooLong = new GuestLoginRequest("a".repeat(13));
        GuestLoginRequest invalidFormat = new GuestLoginRequest("guest!");

        assertThat(validator.validate(tooShort)).isNotEmpty();
        assertThat(validator.validate(tooLong)).isNotEmpty();
        assertThat(validator.validate(invalidFormat)).isNotEmpty();
    }
}
