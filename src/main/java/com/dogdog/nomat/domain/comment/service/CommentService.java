package com.dogdog.nomat.domain.comment.service;

import com.dogdog.nomat.domain.comment.dto.MapCommentListResponse;
import com.dogdog.nomat.domain.comment.entity.MapCommentStatus;
import com.dogdog.nomat.domain.comment.repository.MapCommentRepository;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
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

    private final UserRepository userRepository;
    private final QuizMapRepository quizMapRepository;
    private final MapCommentRepository mapCommentRepository;

    @Transactional(readOnly = true)
    public MapCommentListResponse getMapComments(Long userId, Long mapId, int page, int size, String sort) {
        if (userId != null) {
            validateAuthenticatedUser(userId);
        }
        validatePage(page, size);
        Pageable pageable = PageRequest.of(page, size, sortBy(sort));
        validatePublicPublishedMap(mapId);

        return MapCommentListResponse.from(
                mapCommentRepository.findByMapIdAndStatus(mapId, MapCommentStatus.ACTIVE, pageable),
                userId
        );
    }

    private void validateAuthenticatedUser(Long userId) {
        userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token"));
    }

    private void validatePublicPublishedMap(Long mapId) {
        quizMapRepository.findByIdAndStatusAndVisibility(
                        mapId,
                        MapStatus.PUBLISHED,
                        MapVisibility.PUBLIC
                )
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "map_not_found"));
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
