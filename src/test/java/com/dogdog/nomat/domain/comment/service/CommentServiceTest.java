package com.dogdog.nomat.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.comment.dto.CreateMapCommentRequest;
import com.dogdog.nomat.domain.comment.dto.CreateMapCommentResponse;
import com.dogdog.nomat.domain.comment.dto.MapCommentListResponse;
import com.dogdog.nomat.domain.comment.dto.ModifyMapCommentRequest;
import com.dogdog.nomat.domain.comment.dto.ModifyMapCommentResponse;
import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.comment.entity.MapCommentStatus;
import com.dogdog.nomat.domain.comment.repository.MapCommentRepository;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuizMapRepository quizMapRepository;

    @Mock
    private MapCommentRepository mapCommentRepository;

    @InjectMocks
    private CommentService commentService;

    @Test
    void createMapCommentCreatesCommentAndIncreasesCommentCount() {
        User writer = activeUser(1L);
        Asset profileImage = profileImage(writer);
        ReflectionTestUtils.setField(writer, "profileImageAsset", profileImage);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category);
        ReflectionTestUtils.setField(map, "commentCount", 5L);
        CreateMapCommentRequest request = new CreateMapCommentRequest("재미있는 맵이네요!");

        given(userRepository.findById(1L)).willReturn(Optional.of(writer));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapCommentRepository.save(any(MapComment.class))).willAnswer(invocation -> {
            MapComment comment = invocation.getArgument(0);
            ReflectionTestUtils.setField(comment, "id", 200L);
            ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.of(2026, 8, 21, 10, 0));
            ReflectionTestUtils.setField(comment, "updatedAt", LocalDateTime.of(2026, 8, 21, 10, 0));
            return comment;
        });

        CreateMapCommentResponse response = commentService.createMapComment(1L, 100L, request);

        assertThat(response.commentId()).isEqualTo(200L);
        assertThat(response.content()).isEqualTo("재미있는 맵이네요!");
        assertThat(response.writer().userId()).isEqualTo(1L);
        assertThat(response.writer().nickname()).isEqualTo("tester1");
        assertThat(response.writer().profileImageUrl()).isEqualTo(profileImage.getUrl());
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 21, 10, 0));
        assertThat(map.getCommentCount()).isEqualTo(6L);

        ArgumentCaptor<MapComment> commentCaptor = ArgumentCaptor.forClass(MapComment.class);
        verify(mapCommentRepository).save(commentCaptor.capture());
        MapComment savedComment = commentCaptor.getValue();
        assertThat(savedComment.getMap()).isEqualTo(map);
        assertThat(savedComment.getWriter()).isEqualTo(writer);
        assertThat(savedComment.getContent()).isEqualTo("재미있는 맵이네요!");
        assertThat(savedComment.getStatus()).isEqualTo(MapCommentStatus.ACTIVE);
    }

    @Test
    void createMapCommentRejectsUnknownUser() {
        CreateMapCommentRequest request = new CreateMapCommentRequest("댓글");
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createMapComment(1L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");

        verify(quizMapRepository, never()).findByIdAndStatusAndVisibility(any(), any(), any());
        verify(mapCommentRepository, never()).save(any());
    }

    @Test
    void createMapCommentRejectsBlankContent() {
        User writer = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(writer));

        assertThatThrownBy(() -> commentService.createMapComment(
                1L,
                100L,
                new CreateMapCommentRequest("   ")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(quizMapRepository, never()).findByIdAndStatusAndVisibility(any(), any(), any());
        verify(mapCommentRepository, never()).save(any());
    }

    @Test
    void createMapCommentRejectsTooLongContent() {
        User writer = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(writer));

        assertThatThrownBy(() -> commentService.createMapComment(
                1L,
                100L,
                new CreateMapCommentRequest("a".repeat(301))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("comment_too_long");

        verify(quizMapRepository, never()).findByIdAndStatusAndVisibility(any(), any(), any());
        verify(mapCommentRepository, never()).save(any());
    }

    @Test
    void createMapCommentRejectsUnknownOrPrivateMap() {
        User writer = activeUser(1L);
        CreateMapCommentRequest request = new CreateMapCommentRequest("댓글");

        given(userRepository.findById(1L)).willReturn(Optional.of(writer));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createMapComment(1L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("map_not_found");

        verify(mapCommentRepository, never()).save(any());
    }

    @Test
    void modifyMapCommentUpdatesOwnComment() {
        User writer = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category);
        LocalDateTime beforeUpdatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        MapComment comment = comment(200L, map, writer, "수정 전 댓글", beforeUpdatedAt);

        given(userRepository.findById(1L)).willReturn(Optional.of(writer));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.of(comment));

        ModifyMapCommentResponse response = commentService.modifyMapComment(
                1L,
                200L,
                new ModifyMapCommentRequest("수정된 댓글")
        );

        assertThat(response.commentId()).isEqualTo(200L);
        assertThat(response.content()).isEqualTo("수정된 댓글");
        assertThat(response.updatedAt()).isAfter(beforeUpdatedAt);
        assertThat(comment.getContent()).isEqualTo("수정된 댓글");
        assertThat(comment.getUpdatedAt()).isEqualTo(response.updatedAt());
    }

    @Test
    void modifyMapCommentRejectsOtherWriter() {
        User user = activeUser(1L);
        User writer = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, writer, category);
        MapComment comment = comment(200L, map, writer, "원본 댓글", LocalDateTime.of(2026, 8, 21, 10, 0));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.modifyMapComment(
                1L,
                200L,
                new ModifyMapCommentRequest("수정 시도")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_comment_access");

        assertThat(comment.getContent()).isEqualTo("원본 댓글");
    }

    @Test
    void modifyMapCommentRejectsUnknownOrDeletedComment() {
        User writer = activeUser(1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(writer));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.modifyMapComment(
                1L,
                200L,
                new ModifyMapCommentRequest("수정된 댓글")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("comment_not_found");
    }

    @Test
    void modifyMapCommentRejectsBlankContent() {
        User writer = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(writer));

        assertThatThrownBy(() -> commentService.modifyMapComment(
                1L,
                200L,
                new ModifyMapCommentRequest(" ")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(mapCommentRepository, never()).findByIdAndStatus(any(), any());
    }

    @Test
    void deleteMapCommentDeletesOwnCommentAndDecreasesCommentCount() {
        User writer = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category);
        ReflectionTestUtils.setField(map, "commentCount", 3L);
        MapComment comment = comment(200L, map, writer, "삭제할 댓글", LocalDateTime.of(2026, 1, 1, 10, 0));

        given(userRepository.findById(1L)).willReturn(Optional.of(writer));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.of(comment));

        commentService.deleteMapComment(1L, 200L);

        assertThat(comment.getStatus()).isEqualTo(MapCommentStatus.DELETED);
        assertThat(comment.getDeletedAt()).isNotNull();
        assertThat(comment.getUpdatedAt()).isEqualTo(comment.getDeletedAt());
        assertThat(map.getCommentCount()).isEqualTo(2L);
    }

    @Test
    void deleteMapCommentRejectsOtherWriter() {
        User user = activeUser(1L);
        User writer = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, writer, category);
        ReflectionTestUtils.setField(map, "commentCount", 3L);
        MapComment comment = comment(200L, map, writer, "다른 유저 댓글", LocalDateTime.of(2026, 8, 21, 10, 0));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteMapComment(1L, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_comment_access");

        assertThat(comment.getStatus()).isEqualTo(MapCommentStatus.ACTIVE);
        assertThat(map.getCommentCount()).isEqualTo(3L);
    }

    @Test
    void deleteMapCommentRejectsUnknownOrDeletedComment() {
        User writer = activeUser(1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(writer));
        given(mapCommentRepository.findByIdAndStatus(200L, MapCommentStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteMapComment(1L, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("comment_not_found");
    }

    @Test
    void getMapCommentsReturnsCommentsWithMineStatus() {
        User user = activeUser(1L);
        User writer = activeUser(2L);
        Asset profileImage = profileImage(writer);
        ReflectionTestUtils.setField(writer, "profileImageAsset", profileImage);
        Category category = category(10L);
        QuizMap map = quizMap(100L, writer, category);
        MapComment myComment = comment(200L, map, user, "내 댓글", LocalDateTime.of(2026, 8, 15, 10, 0));
        MapComment otherComment = comment(201L, map, writer, "다른 댓글", LocalDateTime.of(2026, 8, 15, 9, 0));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapCommentRepository.findByMapIdAndStatus(any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(myComment, otherComment)));

        MapCommentListResponse response = commentService.getMapComments(1L, 100L, 0, 20, "latest");

        assertThat(response.comments()).hasSize(2);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();

        MapCommentListResponse.MapCommentResponse first = response.comments().getFirst();
        assertThat(first.commentId()).isEqualTo(200L);
        assertThat(first.content()).isEqualTo("내 댓글");
        assertThat(first.isMine()).isTrue();
        assertThat(first.writer().userId()).isEqualTo(1L);
        assertThat(first.writer().nickname()).isEqualTo("tester1");
        assertThat(first.writer().profileImageUrl()).isNull();

        MapCommentListResponse.MapCommentResponse second = response.comments().get(1);
        assertThat(second.commentId()).isEqualTo(201L);
        assertThat(second.isMine()).isFalse();
        assertThat(second.writer().userId()).isEqualTo(2L);
        assertThat(second.writer().profileImageUrl()).isEqualTo(profileImage.getUrl());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mapCommentRepository).findByMapIdAndStatus(
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(MapCommentStatus.ACTIVE),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection().isDescending())
                .isTrue();
    }

    @Test
    void getMapCommentsReturnsAnonymousCommentsWhenUserIsNull() {
        User writer = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, writer, category);
        MapComment comment = comment(200L, map, writer, "댓글", LocalDateTime.of(2026, 8, 15, 10, 0));

        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapCommentRepository.findByMapIdAndStatus(any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(comment)));

        MapCommentListResponse response = commentService.getMapComments(null, 100L, 0, 20, "oldest");

        assertThat(response.comments()).hasSize(1);
        assertThat(response.comments().getFirst().isMine()).isFalse();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getMapCommentsRejectsUnknownAuthenticatedUser() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getMapComments(1L, 100L, 0, 20, "latest"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");

        verify(quizMapRepository, never()).findByIdAndStatusAndVisibility(any(), any(), any());
    }

    @Test
    void getMapCommentsRejectsUnknownOrPrivateMap() {
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getMapComments(null, 100L, 0, 20, "latest"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("map_not_found");

        verify(mapCommentRepository, never()).findByMapIdAndStatus(any(), any(), any());
    }

    @Test
    void getMapCommentsRejectsInvalidPageRequest() {
        assertThatThrownBy(() -> commentService.getMapComments(null, 100L, -1, 20, "latest"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        assertThatThrownBy(() -> commentService.getMapComments(null, 100L, 0, 101, "latest"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void getMapCommentsRejectsUnsupportedSort() {
        assertThatThrownBy(() -> commentService.getMapComments(null, 100L, 0, 20, "popular"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
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

    private MapComment comment(Long id, QuizMap map, User writer, String content, LocalDateTime createdAt) {
        MapComment comment = MapComment.create(map, writer, content);
        ReflectionTestUtils.setField(comment, "id", id);
        ReflectionTestUtils.setField(comment, "createdAt", createdAt);
        ReflectionTestUtils.setField(comment, "updatedAt", createdAt);
        return comment;
    }

    private Asset profileImage(User uploader) {
        Asset asset = Asset.createImage(
                uploader,
                "profile.png",
                "uploads/images/2026/08/profile.png",
                "https://cdn.nomat.com/uploads/images/2026/08/profile.png",
                "image/png",
                1024L
        );
        ReflectionTestUtils.setField(asset, "id", 30L);
        return asset;
    }
}
