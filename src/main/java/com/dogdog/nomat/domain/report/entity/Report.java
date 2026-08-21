package com.dogdog.nomat.domain.report.entity;

import com.dogdog.nomat.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "reports",
        indexes = {
                @Index(name = "idx_reports_target", columnList = "target_type, target_id"),
                @Index(name = "idx_reports_status_created_at", columnList = "status, created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reports_reporter_target",
                        columnNames = {"reporter_id", "target_type", "target_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "reason", length = 50, nullable = false)
    private String reason;

    @Lob
    @Column(name = "description")
    private String description;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    private Report(
            ReportTargetType targetType,
            Long targetId,
            User reporter,
            String reason,
            String description
    ) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reporter = reporter;
        this.reason = reason;
        this.description = description;
    }

    public static Report createMapReport(
            Long mapId,
            User reporter,
            String reason,
            String description
    ) {
        return new Report(ReportTargetType.MAP, mapId, reporter, reason, description);
    }

    public static Report createCommentReport(
            Long commentId,
            User reporter,
            String reason,
            String description
    ) {
        return new Report(ReportTargetType.MAP_COMMENT, commentId, reporter, reason, description);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
