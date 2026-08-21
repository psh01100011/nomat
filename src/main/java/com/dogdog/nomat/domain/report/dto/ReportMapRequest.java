package com.dogdog.nomat.domain.report.dto;

import jakarta.validation.constraints.Size;

public record ReportMapRequest(
        String reason,
        @Size(max = 500)
        String description
) {
}
