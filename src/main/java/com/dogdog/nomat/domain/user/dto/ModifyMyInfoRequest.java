package com.dogdog.nomat.domain.user.dto;

import jakarta.validation.constraints.Size;

public record ModifyMyInfoRequest(
        @Size(min = 2, max = 20)
        String nickname,

        Long profileImageAssetId
) {
}
