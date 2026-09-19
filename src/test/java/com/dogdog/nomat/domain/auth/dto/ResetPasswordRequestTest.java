package com.dogdog.nomat.domain.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResetPasswordRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsValidPasswordResetRequest() {
        ResetPasswordRequest request = request("newPassword123!", "newPassword123!");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsWeakOrMismatchedPassword() {
        assertThat(validator.validate(request("password", "password"))).isNotEmpty();
        assertThat(validator.validate(request("newPassword123!", "differentPassword123!"))).isNotEmpty();
    }

    private ResetPasswordRequest request(String password, String passwordConfirm) {
        return new ResetPasswordRequest(
                "testuser",
                "tester@example.com",
                "completion-token",
                password,
                passwordConfirm
        );
    }
}
