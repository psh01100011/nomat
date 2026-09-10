package com.dogdog.nomat.domain.comment.service;

import com.dogdog.nomat.domain.comment.dto.CreateMapCommentRequest;
import com.dogdog.nomat.domain.comment.dto.CreateMapCommentResponse;
import com.dogdog.nomat.domain.comment.dto.MapCommentListResponse;
import com.dogdog.nomat.domain.comment.dto.ModifyMapCommentRequest;
import com.dogdog.nomat.domain.comment.dto.ModifyMapCommentResponse;
import com.dogdog.nomat.domain.comment.entity.MapComment;
import com.dogdog.nomat.domain.comment.entity.MapCommentStatus;
import com.dogdog.nomat.domain.comment.repository.MapCommentRepository;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int MAX_COMMENT_CONTENT_LENGTH = 300;

    private final UserRepository userRepository;
    private final QuizMapRepository quizMapRepository;
    private final MapCommentRepository mapCommentRepository;

    @Transactional
    public CreateMapCommentResponse createMapComment(
            Long userId,
            Long mapId,
            CreateMapCommentRequest request
    ) {
        User writer = getAuthenticatedUser(userId);
        if (request == null) {
            throw invalidRequest();
        }
        String content = normalizeContent(request.content());
        QuizMap map = getPublicPublishedMap(mapId);

        MapComment comment = mapCommentRepository.save(MapComment.create(map, writer, content));
        map.increaseCommentCount();

        return CreateMapCommentResponse.from(comment);
    }

    @Transactional
    public ModifyMapCommentResponse modifyMapComment(
            Long userId,
            Long commentId,
            ModifyMapCommentRequest request
    ) {
        getAuthenticatedUser(userId);
        if (request == null) {
            throw invalidRequest();
        }
        String content = normalizeContent(request.content());
        MapComment comment = getActiveComment(commentId);
        validateCommentWriter(comment, userId);

        comment.modify(content);

        return ModifyMapCommentResponse.from(comment);
    }

    @Transactional
    public void deleteMapComment(Long userId, Long commentId) {
        getAuthenticatedUser(userId);
        MapComment comment = getActiveComment(commentId);
        validateCommentWriter(comment, userId);

        comment.delete();
        comment.getMap().decreaseCommentCount();
    }

    @Transactional(readOnly = true)
    public MapCommentListResponse getMapComments(Long userId, Long mapId, int page, int size, String sort) {
        if (userId != null) {
            getAuthenticatedUser(userId);
        }
        validatePage(page, size);
        Pageable pageable = PageRequest.of(page, size, sortBy(sort));
        getPublicPublishedMap(mapId);

        return MapCommentListResponse.from(
                mapCommentRepository.findByMapIdAndStatus(mapId, MapCommentStatus.ACTIVE, pageable),
                userId
        );
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

    private MapComment getActiveComment(Long commentId) {
        return mapCommentRepository.findByIdAndStatus(commentId, MapCommentStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "comment_not_found"));
    }

    private void validateCommentWriter(MapComment comment, Long userId) {
        if (!Objects.equals(comment.getWriter().getId(), userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "forbidden_comment_access");
        }
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw invalidRequest();
        }

        String normalizedContent = content.trim();
        if (!StringUtils.hasText(normalizedContent)) {
            throw invalidRequest();
        }

        if (normalizedContent.length() > MAX_COMMENT_CONTENT_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "comment_too_long");
        }

        return normalizedContent;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalidRequest();
        }
    }

    private Sort sortBy(String sort) {
        String sortValue = StringUtils.hasText(sort) ? sort : "latest";
        return switch (sortValue) {
            case "latest" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case "oldest" -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            default -> throw invalidRequest();
        };
    }

    private BusinessException invalidRequest() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
    }
}
