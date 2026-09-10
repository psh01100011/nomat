package com.dogdog.nomat.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ModifyMyInfoRequest(
        @Size(min = 2, max = 12, message = "invalid_nickname_length")
        @Pattern(regexp = "^[가-힣A-Za-z0-9_]+$", message = "invalid_nickname_format")
        String nickname,

        Long profileImageAssetId,

        @Email(message = "invalid_request")
        @Size(max = 254, message = "invalid_request")
        String email
) {
}
