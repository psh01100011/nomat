package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetProcessingStatus;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.entity.AssetType;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.dto.CreateMapRequest;
import com.dogdog.nomat.domain.map.dto.CreateMapResponse;
import com.dogdog.nomat.domain.map.dto.MapDetailResponse;
import com.dogdog.nomat.domain.map.dto.MapEditorResponse;
import com.dogdog.nomat.domain.map.dto.MapListResponse;
import com.dogdog.nomat.domain.map.dto.SaveMapDraftRequest;
import com.dogdog.nomat.domain.map.dto.SaveMapDraftResponse;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuestionAnswer;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaSourceType;
import com.dogdog.nomat.domain.map.entity.QuestionStatus;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import com.dogdog.nomat.domain.map.repository.CategoryRepository;
import com.dogdog.nomat.domain.map.repository.QuestionAnswerRepository;
import com.dogdog.nomat.domain.map.repository.QuestionMediaRepository;
import com.dogdog.nomat.domain.map.repository.QuestionRepository;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MapService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AssetRepository assetRepository;
    private final QuizMapRepository quizMapRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final QuestionMediaRepository questionMediaRepository;
    private final AudioProcessingJobRepository audioProcessingJobRepository;

    @Transactional
    public CreateMapResponse createMap(Long userId, CreateMapRequest request) {
        User creator = getAuthenticatedUser(userId);
        Category category = getActiveCategory(request.categoryId());
        QuestionType questionType = parseQuestionType(request.questionType());
        MapVisibility visibility = parseVisibility(request.visibility());
        Asset thumbnailAsset = getImageAssetToAttach(request.thumbnailAssetId(), creator);

        validateQuestions(questionType, request.questions());
        MapStatus mapStatus = hasPendingAudioProcessing(request.questions())
                ? MapStatus.PROCESSING
                : MapStatus.PUBLISHED;

        QuizMap map = QuizMap.create(
                creator,
                category,
                thumbnailAsset,
                questionType,
                request.title(),
                request.description(),
                visibility,
                request.questions().size(),
                mapStatus
        );
        QuizMap savedMap = quizMapRepository.save(map);

        for (int index = 0; index < request.questions().size(); index++) {
            saveQuestion(savedMap, questionType, request.questions().get(index), index + 1, creator);
        }

        return CreateMapResponse.from(savedMap);
    }

    @Transactional
    public SaveMapDraftResponse saveMapDraft(Long userId, SaveMapDraftRequest request) {
        User creator = getAuthenticatedUser(userId);
        Category category = getActiveCategoryOrNull(request.categoryId());
        QuestionType questionType = parseQuestionTypeOrNull(request.questionType());
        MapVisibility visibility = parseVisibilityForDraft(request.visibility());
        Asset thumbnailAsset = getImageAssetToAttach(request.thumbnailAssetId(), creator);
        List<SaveMapDraftRequest.QuestionRequest> questions = draftQuestions(request.questions());

        validateDraftQuestions(questionType, questions);

        QuizMap map = QuizMap.draft(
                creator,
                category,
                thumbnailAsset,
                questionType,
                request.title(),
                request.description(),
                visibility,
                questions.size()
        );
        QuizMap savedMap = quizMapRepository.save(map);

        for (int index = 0; index < questions.size(); index++) {
            saveDraftQuestion(savedMap, questionType, questions.get(index), index + 1, creator);
        }

        LocalDateTime savedAt = savedMap.getUpdatedAt() == null ? LocalDateTime.now() : savedMap.getUpdatedAt();
        return SaveMapDraftResponse.of(savedMap, savedAt);
    }

    @Transactional(readOnly = true)
    public MapListResponse getMaps(
            Long userId,
            String keyword,
            Long categoryId,
            String questionTypeValue,
            int page,
            int size,
            String sort,
            Long creatorId
    ) {
        validatePage(page, size);

        QuestionType questionType = parseQuestionTypeOrNull(questionTypeValue);
        boolean viewingOwnMaps = userId != null && Objects.equals(userId, creatorId);
        Collection<MapStatus> statuses = viewingOwnMaps
                ? List.of(MapStatus.DRAFT, MapStatus.PROCESSING, MapStatus.PUBLISHED, MapStatus.BLOCKED)
                : List.of(MapStatus.PUBLISHED);
        MapVisibility visibility = viewingOwnMaps ? null : MapVisibility.PUBLIC;
        Pageable pageable = PageRequest.of(page, size, sortBy(sort));

        Page<QuizMap> maps = quizMapRepository.searchMaps(
                statuses,
                visibility,
                normalizeKeyword(keyword),
                categoryId,
                questionType,
                creatorId,
                pageable
        );

        // TODO: 좋아요/즐겨찾기 도메인 구현 후 userId 기준으로 liked/favorited mapId 목록 조회
        return MapListResponse.from(maps);
    }

    @Transactional(readOnly = true)
    public MapDetailResponse getMap(Long userId, Long mapId) {
        if (userId != null) {
            getAuthenticatedUser(userId);
        }

        QuizMap map = quizMapRepository.findByIdAndStatusAndVisibility(
                        mapId,
                        MapStatus.PUBLISHED,
                        MapVisibility.PUBLIC
                )
                .orElseThrow(this::mapNotFound);

        // TODO: 좋아요/즐겨찾기 도메인 구현 후 userId와 mapId 기준으로 liked/favorited 조회
        return MapDetailResponse.from(map, false, false);
    }

    @Transactional(readOnly = true)
    public MapEditorResponse getMapEditor(Long userId, Long mapId) {
        getAuthenticatedUser(userId);
        QuizMap map = quizMapRepository.findByIdAndStatusNot(mapId, MapStatus.DELETED)
                .orElseThrow(this::mapOrQuestionNotFound);

        if (!Objects.equals(map.getCreator().getId(), userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "forbidden_map_access");
        }

        List<Question> questions = questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(
                mapId,
                QuestionStatus.ACTIVE
        );
        List<Long> questionIds = questions.stream()
                .map(Question::getId)
                .toList();

        Map<Long, List<QuestionAnswer>> answersByQuestionId = questionAnswerRepository
                .findByQuestionIdInOrderByQuestionIdAscIdAsc(questionIds)
                .stream()
                .collect(Collectors.groupingBy(answer -> answer.getQuestion().getId()));

        Map<Long, QuestionMedia> mediaByQuestionId = questionMediaRepository.findByQuestionIdIn(questionIds)
                .stream()
                .collect(Collectors.toMap(media -> media.getQuestion().getId(), Function.identity()));

        return MapEditorResponse.of(map, questions, answersByQuestionId, mediaByQuestionId);
    }

    private User getAuthenticatedUser(Long userId) {
        return userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token"));
    }

    private Category getActiveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(Category::isActive)
                .orElseThrow(this::invalidRequest);
    }

    private Category getActiveCategoryOrNull(Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        return getActiveCategory(categoryId);
    }

    private QuestionType parseQuestionType(String value) {
        return parseEnum(QuestionType.class, value);
    }

    private QuestionType parseQuestionTypeOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return parseQuestionType(value);
    }

    private MapVisibility parseVisibility(String value) {
        if (!StringUtils.hasText(value)) {
            return MapVisibility.PUBLIC;
        }

        return parseEnum(MapVisibility.class, value);
    }

    private MapVisibility parseVisibilityForDraft(String value) {
        if (!StringUtils.hasText(value)) {
            return MapVisibility.PRIVATE;
        }

        return parseEnum(MapVisibility.class, value);
    }

    private QuestionMediaSourceType parseMediaSourceType(String value) {
        return parseEnum(QuestionMediaSourceType.class, value);
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
            case "popular" -> Sort.by(
                    Sort.Order.desc("likeCount"),
                    Sort.Order.desc("favoriteCount"),
                    Sort.Order.desc("playCount"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
            case "mostPlayed" -> Sort.by(
                    Sort.Order.desc("playCount"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
            case "mostLiked" -> Sort.by(
                    Sort.Order.desc("likeCount"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
            default -> throw invalidRequest();
        };
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        return keyword.trim();
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value) {
        if (!StringUtils.hasText(value)) {
            throw invalidRequest();
        }

        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private Asset getImageAssetToAttach(Long assetId, User uploader) {
        if (assetId == null) {
            return null;
        }

        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(this::invalidRequest);
        validateAttachableAsset(asset, uploader, AssetType.IMAGE);
        asset.attach();

        return asset;
    }

    private Asset getMediaAssetToAttach(Long assetId, User uploader, QuestionType questionType) {
        if (assetId == null) {
            throw invalidRequest();
        }

        AssetType assetType = switch (questionType) {
            case IMAGE -> AssetType.IMAGE;
            case AUDIO -> AssetType.AUDIO;
            case TEXT -> throw invalidRequest();
        };

        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(this::invalidRequest);
        validateAttachableAsset(asset, uploader, assetType);
        asset.attach();

        return asset;
    }

    private Asset getDraftMediaAssetToAttach(Long assetId, User uploader, QuestionType questionType) {
        if (assetId == null) {
            return null;
        }

        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(this::invalidRequest);
        if (questionType == QuestionType.IMAGE) {
            validateAttachableAsset(asset, uploader, AssetType.IMAGE);
        } else if (questionType == QuestionType.AUDIO) {
            validateAttachableAsset(asset, uploader, AssetType.AUDIO);
        } else {
            validateAttachableDraftMediaAsset(asset, uploader);
        }
        asset.attach();

        return asset;
    }

    private void validateAttachableAsset(Asset asset, User uploader, AssetType assetType) {
        if (asset.getAssetType() != assetType
                || asset.getStatus() == AssetStatus.DELETED
                || asset.getProcessingStatus() != AssetProcessingStatus.READY
                || !Objects.equals(asset.getUploader().getId(), uploader.getId())) {
            throw invalidRequest();
        }
    }

    private void validateAttachableDraftMediaAsset(Asset asset, User uploader) {
        if ((asset.getAssetType() != AssetType.IMAGE && asset.getAssetType() != AssetType.AUDIO)
                || asset.getStatus() == AssetStatus.DELETED
                || asset.getProcessingStatus() != AssetProcessingStatus.READY
                || !Objects.equals(asset.getUploader().getId(), uploader.getId())) {
            throw invalidRequest();
        }
    }

    private void validateQuestions(QuestionType questionType, List<CreateMapRequest.QuestionRequest> questions) {
        if (questions == null || questions.isEmpty()) {
            throw invalidRequest();
        }

        for (CreateMapRequest.QuestionRequest question : questions) {
            if (!StringUtils.hasText(question.promptText())) {
                throw invalidRequest();
            }

            if (questionType != QuestionType.TEXT && question.media() == null) {
                throw invalidRequest();
            }

            if (questionType == QuestionType.TEXT && question.media() != null) {
                throw invalidRequest();
            }

            validateAnswers(question.answers());
            if (question.media() != null) {
                validateMedia(questionType, question.media());
            }
        }
    }

    private boolean hasPendingAudioProcessing(List<CreateMapRequest.QuestionRequest> questions) {
        return questions.stream()
                .map(CreateMapRequest.QuestionRequest::media)
                .filter(Objects::nonNull)
                .anyMatch(media -> parseMediaSourceType(media.sourceType()) == QuestionMediaSourceType.YOUTUBE);
    }

    private void validateAnswers(List<String> answers) {
        if (answers == null || answers.isEmpty()) {
            throw invalidRequest();
        }

        Set<String> answerKeys = new HashSet<>();
        for (String answer : answers) {
            String answerKey = createAnswerKey(answer);
            if (!answerKeys.add(answerKey)) {
                throw invalidRequest();
            }
        }
    }

    private void validateMedia(QuestionType questionType, CreateMapRequest.MediaRequest media) {
        QuestionMediaSourceType sourceType = parseMediaSourceType(media.sourceType());

        if (sourceType == QuestionMediaSourceType.UPLOAD) {
            if (questionType == QuestionType.TEXT) {
                throw invalidRequest();
            }

            if (media.assetId() == null) {
                throw invalidRequest();
            }
            return;
        }

        if (questionType == QuestionType.IMAGE) {
            throw invalidRequest();
        }

        if (sourceType == QuestionMediaSourceType.YOUTUBE) {
            if (!StringUtils.hasText(media.sourceUrl())) {
                throw invalidRequest();
            }
            validateMediaTimeRange(media.startTimeMs(), media.endTimeMs());
            return;
        }

        if (sourceType == QuestionMediaSourceType.TTS) {
            throw invalidRequest();
        }
    }

    private List<SaveMapDraftRequest.QuestionRequest> draftQuestions(
            List<SaveMapDraftRequest.QuestionRequest> questions
    ) {
        return questions == null ? List.of() : questions;
    }

    private void validateDraftQuestions(
            QuestionType questionType,
            List<SaveMapDraftRequest.QuestionRequest> questions
    ) {
        for (SaveMapDraftRequest.QuestionRequest question : questions) {
            if (question == null) {
                throw invalidRequest();
            }

            if (question.media() != null && StringUtils.hasText(question.media().sourceType())) {
                validateDraftMedia(questionType, question.media());
            }
        }
    }

    private void validateDraftMedia(QuestionType questionType, SaveMapDraftRequest.MediaRequest media) {
        QuestionMediaSourceType sourceType = parseMediaSourceType(media.sourceType());

        if (sourceType == QuestionMediaSourceType.UPLOAD) {
            if (questionType == QuestionType.TEXT) {
                throw invalidRequest();
            }
            return;
        }

        if (sourceType == QuestionMediaSourceType.YOUTUBE) {
            if (questionType == QuestionType.IMAGE || questionType == QuestionType.TEXT) {
                throw invalidRequest();
            }
            validateDraftMediaTimeRange(media.startTimeMs(), media.endTimeMs());
            return;
        }

        if (sourceType == QuestionMediaSourceType.TTS) {
            throw invalidRequest();
        }
    }

    private void validateDraftMediaTimeRange(Long startTimeMs, Long endTimeMs) {
        if (startTimeMs != null) {
            validateNonNegativeMillis(startTimeMs);
        }

        if (endTimeMs != null) {
            validateNonNegativeMillis(endTimeMs);
        }

        if (startTimeMs != null && endTimeMs != null && endTimeMs <= startTimeMs) {
            throw invalidRequest();
        }
    }

    private void validateMediaTimeRange(Long startTimeMs, Long endTimeMs) {
        if (startTimeMs == null || endTimeMs == null || startTimeMs < 0 || endTimeMs <= startTimeMs) {
            throw invalidRequest();
        }

        toIntegerMillis(startTimeMs);
        toIntegerMillis(endTimeMs);
        toIntegerMillis(endTimeMs - startTimeMs);
    }

    private void saveQuestion(
            QuizMap map,
            QuestionType questionType,
            CreateMapRequest.QuestionRequest request,
            int questionOrder,
            User creator
    ) {
        Question question = questionRepository.save(Question.create(map, questionOrder, request.promptText()));

        List<QuestionAnswer> answers = new ArrayList<>();
        for (int index = 0; index < request.answers().size(); index++) {
            String answer = request.answers().get(index);
            answers.add(QuestionAnswer.create(question, answer, createAnswerKey(answer), index == 0));
        }
        questionAnswerRepository.saveAll(answers);

        if (request.media() != null) {
            QuestionMedia media = createQuestionMedia(question, questionType, request.media(), creator);
            questionMediaRepository.save(media);
            if (media.getSourceType() == QuestionMediaSourceType.YOUTUBE) {
                audioProcessingJobRepository.save(AudioProcessingJob.create(media));
            }
        }
    }

    private void saveDraftQuestion(
            QuizMap map,
            QuestionType questionType,
            SaveMapDraftRequest.QuestionRequest request,
            int questionOrder,
            User creator
    ) {
        Question question = questionRepository.save(Question.create(map, questionOrder, request.promptText()));

        List<QuestionAnswer> answers = createDraftAnswers(question, request.answers());
        if (!answers.isEmpty()) {
            questionAnswerRepository.saveAll(answers);
        }

        QuestionMedia media = createDraftQuestionMedia(question, questionType, request.media(), creator);
        if (media != null) {
            questionMediaRepository.save(media);
        }
    }

    private List<QuestionAnswer> createDraftAnswers(Question question, List<String> answerValues) {
        if (answerValues == null || answerValues.isEmpty()) {
            return List.of();
        }

        List<QuestionAnswer> answers = new ArrayList<>();
        Set<String> answerKeys = new HashSet<>();
        for (String answer : answerValues) {
            if (!StringUtils.hasText(answer)) {
                continue;
            }

            String answerKey = createAnswerKey(answer);
            if (answerKeys.add(answerKey)) {
                answers.add(QuestionAnswer.create(question, answer, answerKey, answers.isEmpty()));
            }
        }

        return answers;
    }

    private QuestionMedia createDraftQuestionMedia(
            Question question,
            QuestionType questionType,
            SaveMapDraftRequest.MediaRequest media,
            User creator
    ) {
        if (media == null || !StringUtils.hasText(media.sourceType())) {
            return null;
        }

        QuestionMediaSourceType sourceType = parseMediaSourceType(media.sourceType());
        Asset asset = null;
        Integer startTimeMs = null;
        Integer endTimeMs = null;
        Integer durationMs = null;

        if (sourceType == QuestionMediaSourceType.UPLOAD) {
            asset = getDraftMediaAssetToAttach(media.assetId(), creator, questionType);
        }

        if (sourceType == QuestionMediaSourceType.YOUTUBE) {
            startTimeMs = media.startTimeMs() == null ? null : toIntegerMillis(media.startTimeMs());
            endTimeMs = media.endTimeMs() == null ? null : toIntegerMillis(media.endTimeMs());
            durationMs = startTimeMs == null || endTimeMs == null ? null : endTimeMs - startTimeMs;
        }

        return QuestionMedia.create(
                question,
                asset,
                sourceType,
                media.sourceUrl(),
                startTimeMs,
                endTimeMs,
                durationMs
        );
    }

    private QuestionMedia createQuestionMedia(
            Question question,
            QuestionType questionType,
            CreateMapRequest.MediaRequest media,
            User creator
    ) {
        QuestionMediaSourceType sourceType = parseMediaSourceType(media.sourceType());
        Asset asset = null;
        Integer startTimeMs = null;
        Integer endTimeMs = null;
        Integer durationMs = null;

        if (sourceType == QuestionMediaSourceType.UPLOAD) {
            asset = getMediaAssetToAttach(media.assetId(), creator, questionType);
        }

        if (sourceType == QuestionMediaSourceType.YOUTUBE) {
            startTimeMs = toIntegerMillis(media.startTimeMs());
            endTimeMs = toIntegerMillis(media.endTimeMs());
            durationMs = endTimeMs - startTimeMs;
        }

        return QuestionMedia.create(
                question,
                asset,
                sourceType,
                media.sourceUrl(),
                startTimeMs,
                endTimeMs,
                durationMs
        );
    }

    private String createAnswerKey(String answer) {
        if (!StringUtils.hasText(answer)) {
            throw invalidRequest();
        }

        String answerKey = answer.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(answerKey)) {
            throw invalidRequest();
        }

        return answerKey;
    }

    private void validateNonNegativeMillis(Long value) {
        if (value < 0) {
            throw invalidRequest();
        }

        toIntegerMillis(value);
    }

    private Integer toIntegerMillis(Long value) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException exception) {
            throw invalidRequest();
        }
    }

    private BusinessException invalidRequest() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    private BusinessException mapOrQuestionNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "map_or_question_not_found");
    }

    private BusinessException mapNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "map_not_found");
    }
}
