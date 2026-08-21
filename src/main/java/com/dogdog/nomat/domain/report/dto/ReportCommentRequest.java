package com.dogdog.nomat.domain.report.dto;

import jakarta.validation.constraints.Size;

public record ReportCommentRequest(
        String reason,
        @Size(max = 500)
        String description
) {
}
