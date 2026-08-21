package com.dogdog.nomat.domain.report.dto;

import com.dogdog.nomat.domain.report.entity.Report;

public record ReportMapResponse(
        Long reportId
) {

    public static ReportMapResponse from(Report report) {
        return new ReportMapResponse(report.getId());
    }
}
