package com.dogdog.nomat.domain.report.dto;

import com.dogdog.nomat.domain.report.entity.Report;

public record ReportCommentResponse(
        Long reportId
) {

    public static ReportCommentResponse from(Report report) {
        return new ReportCommentResponse(report.getId());
    }
}
