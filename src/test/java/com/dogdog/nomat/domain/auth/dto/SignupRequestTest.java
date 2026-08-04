package com.dogdog.nomat.domain.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SignupRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validSignupRequestPassesValidation() {
        SignupRequest request = new SignupRequest(
                "testuser",
                "password123!",
                "password123!",
                "tester",
                null
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void passwordRequiresEnglishLetterNumberAndSpecialCharacter() {
        SignupRequest withoutLetter = new SignupRequest("testuser", "12345678!", "12345678!", "tester", null);
        SignupRequest withoutNumber = new SignupRequest("testuser", "password!", "password!", "tester", null);
        SignupRequest withoutSpecialCharacter = new SignupRequest("testuser", "password123", "password123", "tester", null);

        assertThat(validator.validate(withoutLetter)).isNotEmpty();
        assertThat(validator.validate(withoutNumber)).isNotEmpty();
        assertThat(validator.validate(withoutSpecialCharacter)).isNotEmpty();
    }

    @Test
    void passwordConfirmMustMatchPassword() {
        SignupRequest request = new SignupRequest(
                "testuser",
                "password123!",
                "different123!",
                "tester",
                null
        );

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("passwordConfirmed"));
    }
}
