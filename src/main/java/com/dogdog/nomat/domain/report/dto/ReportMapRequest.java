package com.dogdog.nomat.domain.report.dto;

import jakarta.validation.constraints.Size;

public record ReportMapRequest(
        String reason,
        @Size(min = 10, max = 500, message = "invalid_report_description")
        String description
) {
}
