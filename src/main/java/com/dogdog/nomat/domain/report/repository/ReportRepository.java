package com.dogdog.nomat.domain.report.repository;

import com.dogdog.nomat.domain.report.entity.Report;
import com.dogdog.nomat.domain.report.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId,
            ReportTargetType targetType,
            Long targetId
    );
}
