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
import com.dogdog.nomat.domain.map.dto.MapFavoriteResponse;
import com.dogdog.nomat.domain.map.dto.MapLikeResponse;
import com.dogdog.nomat.domain.map.dto.MapListResponse;
import com.dogdog.nomat.domain.map.dto.ModifyMapRequest;
import com.dogdog.nomat.domain.map.dto.ModifyMapResponse;
import com.dogdog.nomat.domain.map.dto.SaveMapDraftRequest;
import com.dogdog.nomat.domain.map.dto.SaveMapDraftResponse;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.MapFavorite;
import com.dogdog.nomat.domain.map.entity.MapFavoriteId;
import com.dogdog.nomat.domain.map.entity.MapLike;
import com.dogdog.nomat.domain.map.entity.MapLikeId;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuestionAnswer;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaProcessingStatus;
import com.dogdog.nomat.domain.map.entity.QuestionMediaSourceType;
import com.dogdog.nomat.domain.map.entity.QuestionStatus;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import com.dogdog.nomat.domain.map.repository.CategoryRepository;
import com.dogdog.nomat.domain.map.repository.MapFavoriteRepository;
import com.dogdog.nomat.domain.map.repository.MapLikeRepository;
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

    private static final int MIN_MAP_TITLE_LENGTH = 2;
    private static final int MAX_MAP_TITLE_LENGTH = 40;
    private static final int MAX_MAP_DESCRIPTION_LENGTH = 500;
    private static final int MAX_QUESTION_COUNT = 300;
    private static final int MAX_QUESTION_PROMPT_LENGTH = 200;
    private static final int MAX_ANSWER_COUNT = 20;
    private static final int MAX_ANSWER_LENGTH = 50;
    private static final long MAX_YOUTUBE_CLIP_DURATION_MS = 90_000L;

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AssetRepository assetRepository;
    private final QuizMapRepository quizMapRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final QuestionMediaRepository questionMediaRepository;
    private final AudioProcessingJobRepository audioProcessingJobRepository;
    private final MapLikeRepository mapLikeRepository;
    private final MapFavoriteRepository mapFavoriteRepository;

    @Transactional
    public CreateMapResponse createMap(Long userId, CreateMapRequest request) {
        User creator = getAuthenticatedUser(userId);
        Category category = getActiveCategory(request.categoryId());
        QuestionType questionType = parseQuestionType(request.questionType());
        MapVisibility visibility = parseVisibility(request.visibility());
        Asset thumbnailAsset = getImageAssetToAttach(request.thumbnailAssetId(), creator);
        String title = normalizeRequiredText(
                request.title(),
                MIN_MAP_TITLE_LENGTH,
                MAX_MAP_TITLE_LENGTH,
                "invalid_map_title_length"
        );
        String description = normalizeOptionalText(request.description(), MAX_MAP_DESCRIPTION_LENGTH, "invalid_request");

        validateQuestions(questionType, request.questions());
        MapStatus mapStatus = hasPendingAudioProcessing(request.questions())
                ? MapStatus.PROCESSING
                : MapStatus.PUBLISHED;

        QuizMap map = QuizMap.create(
                creator,
                category,
                thumbnailAsset,
                questionType,
                title,
                description,
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
        String title = normalizeOptionalText(request.title(), MAX_MAP_TITLE_LENGTH, "invalid_map_title_length");
        String description = normalizeOptionalText(request.description(), MAX_MAP_DESCRIPTION_LENGTH, "invalid_request");

        validateDraftQuestions(questionType, questions);

        QuizMap map = QuizMap.draft(
                creator,
                category,
                thumbnailAsset,
                questionType,
                title,
                description,
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

    @Transactional
    public ModifyMapResponse modifyMap(Long userId, Long mapId, ModifyMapRequest request) {
        User creator = getAuthenticatedUser(userId);
        QuizMap map = quizMapRepository.findByIdAndStatusNot(mapId, MapStatus.DELETED)
                .orElseThrow(this::mapOrQuestionNotFound);

        if (!Objects.equals(map.getCreator().getId(), userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "forbidden_map_access");
        }

        if (map.getVersion() != request.version()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "map_version_conflict",
                    Map.of("currentVersion", map.getVersion())
            );
        }

        MapPatchState patchState = resolveMapPatch(map, request.map(), creator);
        QuestionChanges questionChanges = questionChanges(request.questions());
        List<Question> existingQuestions = questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(
                mapId,
                QuestionStatus.ACTIVE
        );
        Map<Long, Question> existingQuestionsById = existingQuestions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        validateQuestionTargets(existingQuestionsById, questionChanges);

        List<Long> existingQuestionIds = existingQuestions.stream()
                .map(Question::getId)
                .toList();
        Map<Long, List<QuestionAnswer>> answersByQuestionId = questionAnswerRepository
                .findByQuestionIdInOrderByQuestionIdAscIdAsc(existingQuestionIds)
                .stream()
                .collect(Collectors.groupingBy(answer -> answer.getQuestion().getId()));
        Map<Long, QuestionMedia> mediaByQuestionId = questionMediaRepository.findByQuestionIdIn(existingQuestionIds)
                .stream()
                .collect(Collectors.toMap(media -> media.getQuestion().getId(), Function.identity()));

        Set<Long> deletedQuestionIds = new HashSet<>(questionChanges.deleteQuestionIds());
        deleteQuestions(existingQuestionsById, deletedQuestionIds, answersByQuestionId, mediaByQuestionId);
        updateQuestions(
                questionChanges.updateRequests(),
                existingQuestionsById,
                answersByQuestionId,
                mediaByQuestionId,
                patchState.questionType(),
                creator
        );
        List<Question> createdQuestions = new ArrayList<>();
        List<ModifyMapResponse.CreatedQuestionResponse> createdQuestionResponses = createQuestions(
                map,
                questionChanges.createRequests(),
                existingQuestions,
                createdQuestions,
                answersByQuestionId,
                mediaByQuestionId,
                patchState.questionType(),
                creator
        );

        List<Question> activeQuestions = new ArrayList<>();
        for (Question question : existingQuestions) {
            if (!deletedQuestionIds.contains(question.getId())) {
                activeQuestions.add(question);
            }
        }
        activeQuestions.addAll(createdQuestions);

        validateCompleteMapState(
                patchState.title(),
                patchState.category(),
                patchState.questionType(),
                activeQuestions,
                answersByQuestionId,
                mediaByQuestionId
        );

        MapStatus status = hasUnreadyMedia(activeQuestions, mediaByQuestionId)
                ? MapStatus.PROCESSING
                : MapStatus.PUBLISHED;
        map.modify(
                patchState.category(),
                patchState.thumbnailAsset(),
                patchState.questionType(),
                patchState.title(),
                patchState.description(),
                patchState.visibility(),
                activeQuestions.size(),
                status
        );

        LocalDateTime updatedAt = map.getUpdatedAt() == null ? LocalDateTime.now() : map.getUpdatedAt();
        return ModifyMapResponse.of(map, createdQuestionResponses, updatedAt);
    }

    @Transactional
    public void deleteMap(Long userId, Long mapId) {
        getAuthenticatedUser(userId);
        QuizMap map = quizMapRepository.findByIdAndStatusNot(mapId, MapStatus.DELETED)
                .orElseThrow(this::mapNotFound);

        if (!Objects.equals(map.getCreator().getId(), userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "forbidden_map_access");
        }

        audioProcessingJobRepository.deleteByQuestionMediaQuestionMapId(mapId);
        map.delete();
    }

    @Transactional
    public MapLikeResponse likeMap(Long userId, Long mapId) {
        User user = getAuthenticatedUser(userId);
        QuizMap map = getPublicPublishedMap(mapId);
        MapLikeId likeId = new MapLikeId(mapId, userId);

        if (mapLikeRepository.existsById(likeId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "already_liked_map");
        }

        mapLikeRepository.save(MapLike.create(map, user));
        map.increaseLikeCount();

        return new MapLikeResponse(map.getId(), true, map.getLikeCount());
    }

    @Transactional
    public MapLikeResponse unlikeMap(Long userId, Long mapId) {
        getAuthenticatedUser(userId);
        QuizMap map = getPublicPublishedMap(mapId);
        MapLikeId likeId = new MapLikeId(mapId, userId);

        mapLikeRepository.findById(likeId)
                .ifPresent(like -> {
                    mapLikeRepository.delete(like);
                    map.decreaseLikeCount();
                });

        return new MapLikeResponse(map.getId(), false, map.getLikeCount());
    }

    @Transactional
    public MapFavoriteResponse favoriteMap(Long userId, Long mapId) {
        User user = getAuthenticatedUser(userId);
        QuizMap map = getPublicPublishedMap(mapId);
        MapFavoriteId favoriteId = new MapFavoriteId(mapId, userId);

        if (mapFavoriteRepository.existsById(favoriteId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "already_favorited_map");
        }

        mapFavoriteRepository.save(MapFavorite.create(map, user));
        map.increaseFavoriteCount();

        return new MapFavoriteResponse(map.getId(), true, map.getFavoriteCount());
    }

    @Transactional
    public MapFavoriteResponse unfavoriteMap(Long userId, Long mapId) {
        getAuthenticatedUser(userId);
        QuizMap map = getPublicPublishedMap(mapId);
        MapFavoriteId favoriteId = new MapFavoriteId(mapId, userId);

        mapFavoriteRepository.findById(favoriteId)
                .ifPresent(favorite -> {
                    mapFavoriteRepository.delete(favorite);
                    map.decreaseFavoriteCount();
                });

        return new MapFavoriteResponse(map.getId(), false, map.getFavoriteCount());
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

        Set<Long> likedMapIds = getLikedMapIds(userId, maps.getContent());
        Set<Long> favoritedMapIds = getFavoritedMapIds(userId, maps.getContent());
        return MapListResponse.from(maps, likedMapIds, favoritedMapIds);
    }

    @Transactional(readOnly = true)
    public MapDetailResponse getMap(Long userId, Long mapId) {
        if (userId != null) {
            getAuthenticatedUser(userId);
        }

        QuizMap map = getPublicPublishedMap(mapId);

        boolean liked = userId != null && mapLikeRepository.existsById(new MapLikeId(mapId, userId));
        boolean favorited = userId != null && mapFavoriteRepository.existsById(new MapFavoriteId(mapId, userId));
        return MapDetailResponse.from(map, liked, favorited);
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

    private QuizMap getPublicPublishedMap(Long mapId) {
        return quizMapRepository.findByIdAndStatusAndVisibility(
                        mapId,
                        MapStatus.PUBLISHED,
                        MapVisibility.PUBLIC
                )
                .orElseThrow(this::mapNotFound);
    }

    private Set<Long> getLikedMapIds(Long userId, List<QuizMap> maps) {
        if (userId == null || maps.isEmpty()) {
            return Set.of();
        }

        List<Long> mapIds = maps.stream()
                .map(QuizMap::getId)
                .toList();

        return new HashSet<>(mapLikeRepository.findMapIdsByUserIdAndMapIdIn(userId, mapIds));
    }

    private Set<Long> getFavoritedMapIds(Long userId, List<QuizMap> maps) {
        if (userId == null || maps.isEmpty()) {
            return Set.of();
        }

        List<Long> mapIds = maps.stream()
                .map(QuizMap::getId)
                .toList();

        return new HashSet<>(mapFavoriteRepository.findMapIdsByUserIdAndMapIdIn(userId, mapIds));
    }

    private MapPatchState resolveMapPatch(QuizMap map, Map<String, Object> mapNode, User creator) {
        String title = map.getTitle();
        Category category = map.getCategory();
        QuestionType questionType = map.getQuestionType();
        Asset thumbnailAsset = map.getThumbnailAsset();
        String description = map.getDescription();
        MapVisibility visibility = map.getVisibility();

        if (mapNode == null) {
            return new MapPatchState(title, category, questionType, thumbnailAsset, description, visibility);
        }

        if (mapNode.containsKey("title")) {
            title = readTextField(mapNode, "title", true, MAX_MAP_TITLE_LENGTH);
            if (title.length() < MIN_MAP_TITLE_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_map_title_length");
            }
        }

        if (mapNode.containsKey("categoryId")) {
            category = getActiveCategory(readLongField(mapNode, "categoryId"));
        }

        if (mapNode.containsKey("questionType")) {
            questionType = parseQuestionType(readTextField(mapNode, "questionType", true, null));
        }

        if (mapNode.containsKey("thumbnailAssetId")) {
            Object thumbnailValue = mapNode.get("thumbnailAssetId");
            thumbnailAsset = thumbnailValue == null
                    ? null
                    : getImageAssetToAttach(readLongField(mapNode, "thumbnailAssetId"), creator);
        }

        if (mapNode.containsKey("description")) {
            description = readTextField(mapNode, "description", false, MAX_MAP_DESCRIPTION_LENGTH);
        }

        if (mapNode.containsKey("visibility")) {
            visibility = parseVisibility(readTextField(mapNode, "visibility", true, null));
        }

        return new MapPatchState(title, category, questionType, thumbnailAsset, description, visibility);
    }

    private String readTextField(Map<String, Object> node, String fieldName, boolean required, Integer maxLength) {
        Object field = node.get(fieldName);
        if (field == null) {
            if (required) {
                throw invalidRequest();
            }

            return null;
        }

        if (!(field instanceof String value)) {
            throw invalidRequest();
        }

        String normalizedValue = value.trim();
        if (!StringUtils.hasText(normalizedValue)) {
            if (required) {
                throw invalidRequest();
            }

            return null;
        }

        if (maxLength != null && normalizedValue.length() > maxLength) {
            throw invalidRequest();
        }

        return normalizedValue;
    }

    private Long readLongField(Map<String, Object> node, String fieldName) {
        Object field = node.get(fieldName);
        if (!(field instanceof Number value)) {
            throw invalidRequest();
        }

        return value.longValue();
    }

    private QuestionChanges questionChanges(ModifyMapRequest.QuestionsRequest request) {
        if (request == null) {
            return new QuestionChanges(List.of(), List.of(), List.of());
        }

        return new QuestionChanges(
                request.create() == null ? List.of() : request.create(),
                request.update() == null ? List.of() : request.update(),
                request.delete() == null ? List.of() : request.delete()
        );
    }

    private void validateQuestionTargets(Map<Long, Question> existingQuestionsById, QuestionChanges questionChanges) {
        Set<Long> requestedQuestionIds = new HashSet<>();
        for (ModifyMapRequest.UpdateQuestionRequest updateRequest : questionChanges.updateRequests()) {
            if (updateRequest == null || updateRequest.questionId() == null) {
                throw invalidRequest();
            }

            Long questionId = updateRequest.questionId();
            if (!requestedQuestionIds.add(questionId)) {
                throw invalidRequest();
            }

            if (!existingQuestionsById.containsKey(questionId)) {
                throw mapOrQuestionNotFound();
            }
        }

        for (Long questionId : questionChanges.deleteQuestionIds()) {
            if (questionId == null) {
                throw invalidRequest();
            }

            if (!requestedQuestionIds.add(questionId)) {
                throw invalidRequest();
            }

            if (!existingQuestionsById.containsKey(questionId)) {
                throw mapOrQuestionNotFound();
            }
        }
    }

    private void deleteQuestions(
            Map<Long, Question> existingQuestionsById,
            Set<Long> deletedQuestionIds,
            Map<Long, List<QuestionAnswer>> answersByQuestionId,
            Map<Long, QuestionMedia> mediaByQuestionId
    ) {
        if (deletedQuestionIds.isEmpty()) {
            return;
        }

        for (Long questionId : deletedQuestionIds) {
            audioProcessingJobRepository.deleteByQuestionMediaQuestionId(questionId);
            questionMediaRepository.deleteByQuestionId(questionId);
            questionAnswerRepository.deleteByQuestionId(questionId);
            existingQuestionsById.get(questionId).delete();
            answersByQuestionId.remove(questionId);
            mediaByQuestionId.remove(questionId);
        }
    }

    private void updateQuestions(
            List<ModifyMapRequest.UpdateQuestionRequest> updateRequests,
            Map<Long, Question> existingQuestionsById,
            Map<Long, List<QuestionAnswer>> answersByQuestionId,
            Map<Long, QuestionMedia> mediaByQuestionId,
            QuestionType questionType,
            User creator
    ) {
        for (ModifyMapRequest.UpdateQuestionRequest request : updateRequests) {
            validateModifyQuestion(questionType, request.promptText(), request.media(), request.answers());

            Question question = existingQuestionsById.get(request.questionId());
            question.update(normalizeQuestionPrompt(request.promptText()));
            questionAnswerRepository.deleteByQuestionId(question.getId());
            List<QuestionAnswer> answers = createAnswers(question, request.answers());
            questionAnswerRepository.saveAll(answers);
            answersByQuestionId.put(question.getId(), answers);
            updateQuestionMedia(question, questionType, request.media(), creator, mediaByQuestionId);
        }
    }

    private List<ModifyMapResponse.CreatedQuestionResponse> createQuestions(
            QuizMap map,
            List<ModifyMapRequest.CreateQuestionRequest> createRequests,
            List<Question> existingQuestions,
            List<Question> createdQuestions,
            Map<Long, List<QuestionAnswer>> answersByQuestionId,
            Map<Long, QuestionMedia> mediaByQuestionId,
            QuestionType questionType,
            User creator
    ) {
        List<ModifyMapResponse.CreatedQuestionResponse> responses = new ArrayList<>();
        int nextQuestionOrder = existingQuestions.stream()
                .mapToInt(Question::getQuestionOrder)
                .max()
                .orElse(0) + 1;

        for (ModifyMapRequest.CreateQuestionRequest request : createRequests) {
            if (request == null) {
                throw invalidRequest();
            }
            validateModifyQuestion(questionType, request.promptText(), request.media(), request.answers());

            Question question = questionRepository.save(Question.create(
                    map,
                    nextQuestionOrder++,
                    normalizeQuestionPrompt(request.promptText())
            ));
            List<QuestionAnswer> answers = createAnswers(question, request.answers());
            questionAnswerRepository.saveAll(answers);
            QuestionMedia media = createModifiedQuestionMedia(question, questionType, request.media(), creator);
            if (media != null) {
                questionMediaRepository.save(media);
                syncAudioProcessingJob(media);
                mediaByQuestionId.put(question.getId(), media);
            }

            createdQuestions.add(question);
            answersByQuestionId.put(question.getId(), answers);
            responses.add(new ModifyMapResponse.CreatedQuestionResponse(request.clientId(), question.getId()));
        }

        return responses;
    }

    private void validateModifyQuestion(
            QuestionType questionType,
            String promptText,
            ModifyMapRequest.MediaRequest media,
            List<String> answers
    ) {
        if (questionType == null) {
            throw invalidRequest();
        }

        validateQuestionCore(questionType, promptText, media, answers);
    }

    private void validateQuestionCore(
            QuestionType questionType,
            String promptText,
            ModifyMapRequest.MediaRequest media,
            List<String> answers
    ) {
        validateQuestionPrompt(promptText);

        if (questionType != QuestionType.TEXT && media == null) {
            throw invalidRequest();
        }

        if (questionType == QuestionType.TEXT && media != null) {
            throw invalidRequest();
        }

        validateAnswers(answers);
        if (media != null) {
            validateMedia(questionType, media);
        }
    }

    private List<QuestionAnswer> createAnswers(Question question, List<String> answerValues) {
        List<QuestionAnswer> answers = new ArrayList<>();
        for (int index = 0; index < answerValues.size(); index++) {
            String answer = normalizeAnswer(answerValues.get(index));
            answers.add(QuestionAnswer.create(question, answer, createAnswerKey(answer), index == 0));
        }

        return answers;
    }

    private void updateQuestionMedia(
            Question question,
            QuestionType questionType,
            ModifyMapRequest.MediaRequest mediaRequest,
            User creator,
            Map<Long, QuestionMedia> mediaByQuestionId
    ) {
        if (mediaRequest == null) {
            removeQuestionMedia(question.getId(), mediaByQuestionId);
            return;
        }

        QuestionMedia existingMedia = mediaByQuestionId.get(question.getId());
        QuestionMedia newMedia = createModifiedQuestionMedia(question, questionType, mediaRequest, creator);
        if (existingMedia == null) {
            questionMediaRepository.save(newMedia);
            mediaByQuestionId.put(question.getId(), newMedia);
            syncAudioProcessingJob(newMedia);
            return;
        }

        existingMedia.update(
                newMedia.getAsset(),
                newMedia.getSourceType(),
                newMedia.getSourceUrl(),
                newMedia.getStartTimeMs(),
                newMedia.getEndTimeMs(),
                newMedia.getDurationMs()
        );
        mediaByQuestionId.put(question.getId(), existingMedia);
        syncAudioProcessingJob(existingMedia);
    }

    private void removeQuestionMedia(Long questionId, Map<Long, QuestionMedia> mediaByQuestionId) {
        QuestionMedia existingMedia = mediaByQuestionId.remove(questionId);
        if (existingMedia == null) {
            return;
        }

        audioProcessingJobRepository.deleteByQuestionMediaQuestionId(questionId);
        questionMediaRepository.deleteByQuestionId(questionId);
    }

    private QuestionMedia createModifiedQuestionMedia(
            Question question,
            QuestionType questionType,
            ModifyMapRequest.MediaRequest media,
            User creator
    ) {
        if (media == null) {
            return null;
        }

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

    private void syncAudioProcessingJob(QuestionMedia media) {
        if (media.getSourceType() != QuestionMediaSourceType.YOUTUBE) {
            if (media.getId() != null) {
                audioProcessingJobRepository.deleteByQuestionMediaId(media.getId());
            }
            return;
        }

        if (media.getId() == null) {
            audioProcessingJobRepository.save(AudioProcessingJob.create(media));
            return;
        }

        audioProcessingJobRepository.findByQuestionMediaId(media.getId())
                .ifPresentOrElse(
                        AudioProcessingJob::reset,
                        () -> audioProcessingJobRepository.save(AudioProcessingJob.create(media))
                );
    }

    private void validateCompleteMapState(
            String title,
            Category category,
            QuestionType questionType,
            List<Question> activeQuestions,
            Map<Long, List<QuestionAnswer>> answersByQuestionId,
            Map<Long, QuestionMedia> mediaByQuestionId
    ) {
        if (!StringUtils.hasText(title) || category == null || questionType == null || activeQuestions.isEmpty()) {
            throw invalidRequest();
        }

        if (activeQuestions.size() > MAX_QUESTION_COUNT) {
            throw invalidRequest();
        }

        for (Question question : activeQuestions) {
            validateQuestionPrompt(question.getPromptText());

            List<QuestionAnswer> answers = answersByQuestionId.getOrDefault(question.getId(), List.of());
            validatePersistedAnswers(answers);
            validatePersistedMedia(questionType, mediaByQuestionId.get(question.getId()));
        }
    }

    private void validatePersistedAnswers(List<QuestionAnswer> answers) {
        if (answers.isEmpty() || answers.size() > MAX_ANSWER_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_answer_count");
        }

        Set<String> answerKeys = new HashSet<>();
        for (QuestionAnswer answer : answers) {
            validateAnswer(answer.getAnswerText());
            if (!StringUtils.hasText(answer.getAnswerKey())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_answer_length");
            }
            if (!answerKeys.add(answer.getAnswerKey())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "duplicate_answer");
            }
        }
    }

    private void validatePersistedMedia(QuestionType questionType, QuestionMedia media) {
        if (questionType == QuestionType.TEXT) {
            if (media != null) {
                throw invalidRequest();
            }
            return;
        }

        if (media == null) {
            throw invalidRequest();
        }

        if (media.getSourceType() == QuestionMediaSourceType.UPLOAD) {
            if (media.getAsset() == null) {
                throw invalidRequest();
            }

            AssetType expectedAssetType = switch (questionType) {
                case IMAGE -> AssetType.IMAGE;
                case AUDIO -> AssetType.AUDIO;
                case TEXT -> throw invalidRequest();
            };
            if (media.getAsset().getAssetType() != expectedAssetType) {
                throw invalidRequest();
            }
            return;
        }

        if (questionType == QuestionType.IMAGE) {
            throw invalidRequest();
        }

        if (media.getSourceType() == QuestionMediaSourceType.YOUTUBE) {
            if (!StringUtils.hasText(media.getSourceUrl())) {
                throw invalidRequest();
            }
            validateMediaTimeRange(
                    media.getStartTimeMs() == null ? null : media.getStartTimeMs().longValue(),
                    media.getEndTimeMs() == null ? null : media.getEndTimeMs().longValue()
            );
            return;
        }

        if (media.getSourceType() == QuestionMediaSourceType.TTS) {
            throw invalidRequest();
        }
    }

    private boolean hasUnreadyMedia(List<Question> activeQuestions, Map<Long, QuestionMedia> mediaByQuestionId) {
        return activeQuestions.stream()
                .map(question -> mediaByQuestionId.get(question.getId()))
                .filter(Objects::nonNull)
                .anyMatch(media -> media.getProcessingStatus() != QuestionMediaProcessingStatus.READY);
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

        if (questions.size() > MAX_QUESTION_COUNT) {
            throw invalidRequest();
        }

        for (CreateMapRequest.QuestionRequest question : questions) {
            validateQuestionPrompt(question.promptText());

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
        if (answers == null || answers.isEmpty() || answers.size() > MAX_ANSWER_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_answer_count");
        }

        Set<String> answerKeys = new HashSet<>();
        for (String answer : answers) {
            validateAnswer(answer);
            String answerKey = createAnswerKey(answer);
            if (!answerKeys.add(answerKey)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "duplicate_answer");
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

    private void validateMedia(QuestionType questionType, ModifyMapRequest.MediaRequest media) {
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
        if (questions.size() > MAX_QUESTION_COUNT) {
            throw invalidRequest();
        }

        for (SaveMapDraftRequest.QuestionRequest question : questions) {
            if (question == null) {
                throw invalidRequest();
            }

            validateDraftQuestionPrompt(question.promptText());
            validateDraftAnswers(question.answers());

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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_youtube_clip_range");
        }

        if (startTimeMs != null && endTimeMs != null && endTimeMs - startTimeMs > MAX_YOUTUBE_CLIP_DURATION_MS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "youtube_clip_too_long");
        }
    }

    private void validateMediaTimeRange(Long startTimeMs, Long endTimeMs) {
        if (startTimeMs == null || endTimeMs == null || startTimeMs < 0 || endTimeMs <= startTimeMs) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_youtube_clip_range");
        }

        if (endTimeMs - startTimeMs > MAX_YOUTUBE_CLIP_DURATION_MS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "youtube_clip_too_long");
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
        Question question = questionRepository.save(Question.create(
                map,
                questionOrder,
                normalizeQuestionPrompt(request.promptText())
        ));

        List<QuestionAnswer> answers = new ArrayList<>();
        for (int index = 0; index < request.answers().size(); index++) {
            String answer = normalizeAnswer(request.answers().get(index));
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
        Question question = questionRepository.save(Question.create(
                map,
                questionOrder,
                normalizeOptionalQuestionPrompt(request.promptText())
        ));

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

            String normalizedAnswer = normalizeAnswer(answer);
            String answerKey = createAnswerKey(normalizedAnswer);
            if (answerKeys.add(answerKey)) {
                answers.add(QuestionAnswer.create(question, normalizedAnswer, answerKey, answers.isEmpty()));
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

    private void validateQuestionPrompt(String promptText) {
        normalizeQuestionPrompt(promptText);
    }

    private String normalizeQuestionPrompt(String promptText) {
        return normalizeRequiredText(
                promptText,
                1,
                MAX_QUESTION_PROMPT_LENGTH,
                "invalid_question_prompt_length"
        );
    }

    private void validateDraftQuestionPrompt(String promptText) {
        if (!StringUtils.hasText(promptText)) {
            return;
        }

        normalizeOptionalQuestionPrompt(promptText);
    }

    private String normalizeOptionalQuestionPrompt(String promptText) {
        return normalizeOptionalText(promptText, MAX_QUESTION_PROMPT_LENGTH, "invalid_question_prompt_length");
    }

    private void validateDraftAnswers(List<String> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }

        if (answers.size() > MAX_ANSWER_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_answer_count");
        }

        Set<String> answerKeys = new HashSet<>();
        for (String answer : answers) {
            if (!StringUtils.hasText(answer)) {
                continue;
            }

            validateAnswer(answer);
            String answerKey = createAnswerKey(answer);
            if (!answerKeys.add(answerKey)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "duplicate_answer");
            }
        }
    }

    private void validateAnswer(String answer) {
        normalizeAnswer(answer);
    }

    private String normalizeAnswer(String answer) {
        return normalizeRequiredText(answer, 1, MAX_ANSWER_LENGTH, "invalid_answer_length");
    }

    private String normalizeRequiredText(String value, int minLength, int maxLength, String messageCode) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, messageCode);
        }

        String normalizedValue = value.trim();
        if (normalizedValue.length() < minLength || normalizedValue.length() > maxLength) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, messageCode);
        }

        return normalizedValue;
    }

    private String normalizeOptionalText(String value, int maxLength, String messageCode) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, messageCode);
        }

        return normalizedValue;
    }

    private String createAnswerKey(String answer) {
        if (!StringUtils.hasText(answer)) {
            throw invalidRequest();
        }

        String answerKey = answer.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
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

    private record MapPatchState(
            String title,
            Category category,
            QuestionType questionType,
            Asset thumbnailAsset,
            String description,
            MapVisibility visibility
    ) {
    }

    private record QuestionChanges(
            List<ModifyMapRequest.CreateQuestionRequest> createRequests,
            List<ModifyMapRequest.UpdateQuestionRequest> updateRequests,
            List<Long> deleteQuestionIds
    ) {
    }
}
