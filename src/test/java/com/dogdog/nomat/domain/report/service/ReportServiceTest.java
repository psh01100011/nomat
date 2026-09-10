package com.dogdog.nomat.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.comment.entity.MapCommentStatus;
import com.dogdog.nomat.domain.comment.repository.MapCommentRepository;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.report.dto.ReportCommentRequest;
import com.dogdog.nomat.domain.report.dto.ReportCommentResponse;
import com.dogdog.nomat.domain.report.dto.ReportMapRequest;
import com.dogdog.nomat.domain.report.dto.ReportMapResponse;
import com.dogdog.nomat.domain.report.entity.Report;
import com.dogdog.nomat.domain.report.entity.ReportStatus;
import com.dogdog.nomat.domain.report.entity.ReportTargetType;
import com.dogdog.nomat.domain.report.repository.ReportRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuizMapRepository quizMapRepository;

    @Mock
    private MapCommentRepository mapCommentRepository;

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    void reportMapCreatesPendingMapReport() {
        User reporter = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category);
        ReportMapRequest request = new ReportMapRequest(
                "COPYRIGHT",
                "저작권이 있는 음원이 무단으로 사용된 것 같습니다."
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.MAP, 100L))
                .willReturn(false);
        given(reportRepository.saveAndFlush(any(Report.class))).willAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 301L);
            return report;
        });

        ReportMapResponse response = reportService.reportMap(1L, 100L, request);

        assertThat(response.reportId()).isEqualTo(301L);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        Report savedReport = reportCaptor.getValue();
        assertThat(savedReport.getTargetType()).isEqualTo(ReportTargetType.MAP);
        assertThat(savedReport.getTargetId()).isEqualTo(100L);
        assertThat(savedReport.getReporter()).isEqualTo(reporter);
        assertThat(savedReport.getReason()).isEqualTo("COPYRIGHT");
        assertThat(savedReport.getDescription()).isEqualTo("저작권이 있는 음원이 무단으로 사용된 것 같습니다.");
        assertThat(savedReport.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void reportMapAcceptsRecommendedMapReportReasons() {
        User reporter = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category);

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.MAP, 100L))
                .willReturn(false);
        given(reportRepository.saveAndFlush(any(Report.class))).willAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 301L);
            return report;
        });

        for (String reason : List.of(
                "INAPPROPRIATE",
                "COPYRIGHT",
                "SPAM",
                "OFFENSIVE",
                "BROKEN_CONTENT",
                "WRONG_ANSWER",
                "OTHER"
        )) {
            ReportMapRequest request = new ReportMapRequest(
                    reason,
                    reason.equals("OTHER") ? "기타 신고 사유입니다." : null
            );

            ReportMapResponse response = reportService.reportMap(1L, 100L, request);

            assertThat(response.reportId()).isEqualTo(301L);
        }
    }

    @Test
    void reportMapNormalizesReasonAndDescription() {
        User reporter = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category);
        ReportMapRequest request = new ReportMapRequest(" other ", "  기타 신고 사유입니다.  ");

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.MAP, 100L))
                .willReturn(false);
        given(reportRepository.saveAndFlush(any(Report.class))).willAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 302L);
            return report;
        });

        reportService.reportMap(1L, 100L, request);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getReason()).isEqualTo("OTHER");
        assertThat(reportCaptor.getValue().getDescription()).isEqualTo("기타 신고 사유입니다.");
    }

    @Test
    void reportMapRejectsUnknownUser() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.reportMap(
                1L,
                100L,
                new ReportMapRequest("SPAM", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");

        verify(quizMapRepository, never()).findByIdAndStatusAndVisibility(any(), any(), any());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportMapRejectsUnsupportedReason() {
        User reporter = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.reportMap(
                1L,
                100L,
                new ReportMapRequest("FALSE_INFORMATION", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(quizMapRepository, never()).findByIdAndStatusAndVisibility(any(), any(), any());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportMapRejectsOtherReasonWithoutDescription() {
        User reporter = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.reportMap(
                1L,
                100L,
                new ReportMapRequest("OTHER", " ")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_report_description");

        verify(quizMapRepository, never()).findByIdAndStatusAndVisibility(any(), any(), any());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportMapRejectsTooLongDescription() {
        User reporter = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.reportMap(
                1L,
                100L,
                new ReportMapRequest("SPAM", "a".repeat(501))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_report_description");

        verify(quizMapRepository, never()).findByIdAndStatusAndVisibility(any(), any(), any());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportMapRejectsUnknownOrPrivateMap() {
        User reporter = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.reportMap(
                1L,
                100L,
                new ReportMapRequest("SPAM", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("map_not_found");

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportMapRejectsAlreadyReportedMap() {
        User reporter = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category);

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.MAP, 100L))
                .willReturn(true);

        assertThatThrownBy(() -> reportService.reportMap(
                1L,
                100L,
                new ReportMapRequest("SPAM", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already_reported_map");

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportMapRejectsDuplicateReportCreatedAtSameTime() {
        User reporter = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category);

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.MAP, 100L))
                .willReturn(false);
        given(reportRepository.saveAndFlush(any(Report.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> reportService.reportMap(
                1L,
                100L,
                new ReportMapRequest("SPAM", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already_reported_map");
    }

    @Test
    void reportCommentCreatesPendingCommentReport() {
        User reporter = activeUser(1L);
        User writer = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, writer, category);
        MapComment comment = comment(200L, map, writer, "신고 대상 댓글");
        ReportCommentRequest request = new ReportCommentRequest("INAPPROPRIATE", "욕설이 포함된 댓글입니다.");

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.of(comment));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.MAP_COMMENT, 200L))
                .willReturn(false);
        given(reportRepository.saveAndFlush(any(Report.class))).willAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 302L);
            return report;
        });

        ReportCommentResponse response = reportService.reportComment(1L, 200L, request);

        assertThat(response.reportId()).isEqualTo(302L);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        Report savedReport = reportCaptor.getValue();
        assertThat(savedReport.getTargetType()).isEqualTo(ReportTargetType.MAP_COMMENT);
        assertThat(savedReport.getTargetId()).isEqualTo(200L);
        assertThat(savedReport.getReporter()).isEqualTo(reporter);
        assertThat(savedReport.getReason()).isEqualTo("INAPPROPRIATE");
        assertThat(savedReport.getDescription()).isEqualTo("욕설이 포함된 댓글입니다.");
        assertThat(savedReport.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void reportCommentAcceptsCommentReportReasons() {
        User reporter = activeUser(1L);
        User writer = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, writer, category);
        MapComment comment = comment(200L, map, writer, "신고 대상 댓글");

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.of(comment));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.MAP_COMMENT, 200L))
                .willReturn(false);
        given(reportRepository.saveAndFlush(any(Report.class))).willAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 302L);
            return report;
        });

        for (String reason : List.of(
                "INAPPROPRIATE",
                "SPAM",
                "OFFENSIVE",
                "HARASSMENT",
                "PERSONAL_INFO",
                "FALSE_INFORMATION",
                "OTHER"
        )) {
            ReportCommentRequest request = new ReportCommentRequest(
                    reason,
                    reason.equals("OTHER") ? "기타 신고 사유입니다." : null
            );

            ReportCommentResponse response = reportService.reportComment(1L, 200L, request);

            assertThat(response.reportId()).isEqualTo(302L);
        }
    }

    @Test
    void reportCommentRejectsUnsupportedReason() {
        User reporter = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.reportComment(
                1L,
                200L,
                new ReportCommentRequest("COPYRIGHT", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(mapCommentRepository, never()).findByIdAndStatus(any(), any());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportCommentRejectsOtherReasonWithoutDescription() {
        User reporter = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.reportComment(
                1L,
                200L,
                new ReportCommentRequest("OTHER", " ")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_report_description");

        verify(mapCommentRepository, never()).findByIdAndStatus(any(), any());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportCommentRejectsUnknownOrDeletedComment() {
        User reporter = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.reportComment(
                1L,
                200L,
                new ReportCommentRequest("SPAM", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("comment_not_found");

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportCommentRejectsCommentOnPrivateMap() {
        User reporter = activeUser(1L);
        User writer = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, writer, category);
        ReflectionTestUtils.setField(map, "visibility", MapVisibility.PRIVATE);
        MapComment comment = comment(200L, map, writer, "신고 대상 댓글");

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> reportService.reportComment(
                1L,
                200L,
                new ReportCommentRequest("SPAM", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("comment_not_found");

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportCommentRejectsAlreadyReportedComment() {
        User reporter = activeUser(1L);
        User writer = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, writer, category);
        MapComment comment = comment(200L, map, writer, "신고 대상 댓글");

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.of(comment));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.MAP_COMMENT, 200L))
                .willReturn(true);

        assertThatThrownBy(() -> reportService.reportComment(
                1L,
                200L,
                new ReportCommentRequest("SPAM", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already_reported_comment");

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportCommentRejectsDuplicateReportCreatedAtSameTime() {
        User reporter = activeUser(1L);
        User writer = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, writer, category);
        MapComment comment = comment(200L, map, writer, "신고 대상 댓글");

        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.of(comment));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.MAP_COMMENT, 200L))
                .willReturn(false);
        given(reportRepository.saveAndFlush(any(Report.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> reportService.reportComment(
                1L,
                200L,
                new ReportCommentRequest("SPAM", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already_reported_comment");
    }

    private User activeUser(Long id) {
        User user = User.create("testuser" + id, "encoded-password", "tester" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Category category(Long id) {
        Category category = Category.create("음악");
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private QuizMap quizMap(Long id, User creator, Category category) {
        QuizMap map = QuizMap.create(
                creator,
                category,
                null,
                QuestionType.AUDIO,
                "오디오 퀴즈",
                "설명",
                MapVisibility.PUBLIC,
                1,
                MapStatus.PUBLISHED
        );
        ReflectionTestUtils.setField(map, "id", id);
        return map;
    }

    private MapComment comment(Long id, QuizMap map, User writer, String content) {
        MapComment comment = MapComment.create(map, writer, content);
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }
}
