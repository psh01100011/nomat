package com.dogdog.nomat.domain.report.service;

import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.comment.entity.MapCommentStatus;
import com.dogdog.nomat.domain.comment.repository.MapCommentRepository;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.report.dto.ReportCommentRequest;
import com.dogdog.nomat.domain.report.dto.ReportCommentResponse;
import com.dogdog.nomat.domain.report.dto.ReportMapRequest;
import com.dogdog.nomat.domain.report.dto.ReportMapResponse;
import com.dogdog.nomat.domain.report.entity.CommentReportReason;
import com.dogdog.nomat.domain.report.entity.MapReportReason;
import com.dogdog.nomat.domain.report.entity.Report;
import com.dogdog.nomat.domain.report.entity.ReportTargetType;
import com.dogdog.nomat.domain.report.repository.ReportRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private static final int MIN_REPORT_DESCRIPTION_LENGTH = 10;
    private static final int MAX_REPORT_DESCRIPTION_LENGTH = 500;

    private final UserRepository userRepository;
    private final QuizMapRepository quizMapRepository;
    private final MapCommentRepository mapCommentRepository;
    private final ReportRepository reportRepository;

    @Transactional
    public ReportMapResponse reportMap(Long userId, Long mapId, ReportMapRequest request) {
        User reporter = getAuthenticatedUser(userId);
        if (request == null) {
            throw invalidRequest();
        }

        MapReportReason reason = parseMapReportReason(request.reason());
        String description = normalizeDescription(request.description());
        validateDescription(reason == MapReportReason.OTHER, description);

        QuizMap map = getPublicPublishedMap(mapId);
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporter.getId(),
                ReportTargetType.MAP,
                map.getId()
        )) {
            throw alreadyReportedMap();
        }

        try {
            Report report = reportRepository.saveAndFlush(Report.createMapReport(
                    map.getId(),
                    reporter,
                    reason.name(),
                    description
            ));
            log.info(
                    "event=report_created reportId={} targetType=MAP targetId={} userId={} reason={}",
                    report.getId(), mapId, userId, reason
            );
            return ReportMapResponse.from(report);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyReportedMap();
        }
    }

    @Transactional
    public ReportCommentResponse reportComment(Long userId, Long commentId, ReportCommentRequest request) {
        User reporter = getAuthenticatedUser(userId);
        if (request == null) {
            throw invalidRequest();
        }

        CommentReportReason reason = parseCommentReportReason(request.reason());
        String description = normalizeDescription(request.description());
        validateDescription(reason == CommentReportReason.OTHER, description);

        MapComment comment = getReportableComment(commentId);
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporter.getId(),
                ReportTargetType.MAP_COMMENT,
                comment.getId()
        )) {
            throw alreadyReportedComment();
        }

        try {
            Report report = reportRepository.saveAndFlush(Report.createCommentReport(
                    comment.getId(),
                    reporter,
                    reason.name(),
                    description
            ));
            log.info(
                    "event=report_created reportId={} targetType=MAP_COMMENT targetId={} userId={} reason={}",
                    report.getId(), commentId, userId, reason
            );
            return ReportCommentResponse.from(report);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyReportedComment();
        }
    }

    private User getAuthenticatedUser(Long userId) {
        return userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token"));
    }

    private QuizMap getPublicPublishedMap(Long mapId) {
        return quizMapRepository.findByIdAndStatusAndVisibility(
                        mapId,
                        MapStatus.PUBLISHED,
                        MapVisibility.PUBLIC
                )
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "map_not_found"));
    }

    private MapComment getReportableComment(Long commentId) {
        MapComment comment = mapCommentRepository.findByIdAndStatus(commentId, MapCommentStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "comment_not_found"));
        QuizMap map = comment.getMap();
        if (map.getStatus() != MapStatus.PUBLISHED || map.getVisibility() != MapVisibility.PUBLIC) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "comment_not_found");
        }

        return comment;
    }

    private MapReportReason parseMapReportReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw invalidRequest();
        }

        try {
            return MapReportReason.valueOf(reason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private CommentReportReason parseCommentReportReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw invalidRequest();
        }

        try {
            return CommentReportReason.valueOf(reason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }

        return description.trim();
    }

    private void validateDescription(boolean otherReason, String description) {
        if (otherReason && !StringUtils.hasText(description)) {
            throw invalidReportDescription();
        }

        if (description != null
                && (description.length() < MIN_REPORT_DESCRIPTION_LENGTH
                || description.length() > MAX_REPORT_DESCRIPTION_LENGTH)) {
            throw invalidReportDescription();
        }
    }

    private BusinessException invalidRequest() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    private BusinessException invalidReportDescription() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_report_description");
    }

    private BusinessException alreadyReportedMap() {
        return new BusinessException(HttpStatus.CONFLICT, "already_reported_map");
    }

    private BusinessException alreadyReportedComment() {
        return new BusinessException(HttpStatus.CONFLICT, "already_reported_comment");
    }
}
