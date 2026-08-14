package com.dogdog.nomat.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.dto.CreateMapRequest;
import com.dogdog.nomat.domain.map.dto.CreateMapResponse;
import com.dogdog.nomat.domain.map.dto.MapDetailResponse;
import com.dogdog.nomat.domain.map.dto.MapEditorResponse;
import com.dogdog.nomat.domain.map.dto.MapListResponse;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuestionAnswer;
import com.dogdog.nomat.domain.map.entity.QuestionMedia;
import com.dogdog.nomat.domain.map.entity.QuestionMediaProcessingStatus;
import com.dogdog.nomat.domain.map.entity.QuestionMediaSourceType;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.AudioProcessingJobRepository;
import com.dogdog.nomat.domain.map.repository.CategoryRepository;
import com.dogdog.nomat.domain.map.repository.QuestionAnswerRepository;
import com.dogdog.nomat.domain.map.repository.QuestionMediaRepository;
import com.dogdog.nomat.domain.map.repository.QuestionRepository;
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
class MapServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private QuizMapRepository quizMapRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionAnswerRepository questionAnswerRepository;

    @Mock
    private QuestionMediaRepository questionMediaRepository;

    @Mock
    private AudioProcessingJobRepository audioProcessingJobRepository;

    @InjectMocks
    private MapService mapService;

    @Test
    void getMapReturnsPublicPublishedMapDetail() {
        User creator = activeUser(1L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "playCount", 135L);
        ReflectionTestUtils.setField(map, "likeCount", 12L);
        ReflectionTestUtils.setField(map, "favoriteCount", 4L);
        ReflectionTestUtils.setField(map, "commentCount", 5L);

        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));

        MapDetailResponse response = mapService.getMap(null, 100L);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.title()).isEqualTo("오디오 퀴즈");
        assertThat(response.category().categoryId()).isEqualTo(10L);
        assertThat(response.category().name()).isEqualTo("음악");
        assertThat(response.questionType()).isEqualTo("AUDIO");
        assertThat(response.thumbnailUrl()).isNull();
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.creator().userId()).isEqualTo(1L);
        assertThat(response.creator().nickname()).isEqualTo("tester1");
        assertThat(response.creator().profileImageUrl()).isNull();
        assertThat(response.questionCount()).isEqualTo(1);
        assertThat(response.playCount()).isEqualTo(135L);
        assertThat(response.likeCount()).isEqualTo(12L);
        assertThat(response.favoriteCount()).isEqualTo(4L);
        assertThat(response.commentCount()).isEqualTo(5L);
        assertThat(response.liked()).isFalse();
        assertThat(response.favorited()).isFalse();
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 10, 0));
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 10, 30));
    }

    @Test
    void getMapRejectsMapThatIsNotPublicPublished() {
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> mapService.getMap(null, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("map_not_found");
    }

    @Test
    void getMapRejectsUnknownAuthenticatedUser() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mapService.getMap(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");

        verify(quizMapRepository, never()).findByIdAndStatusAndVisibility(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMapsReturnsPublicPublishedMaps() {
        User creator = activeUser(1L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "playCount", 135L);
        ReflectionTestUtils.setField(map, "likeCount", 12L);
        ReflectionTestUtils.setField(map, "favoriteCount", 4L);
        ReflectionTestUtils.setField(map, "commentCount", 5L);

        given(quizMapRepository.searchMaps(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(map)));

        MapListResponse response = mapService.getMaps(
                null,
                "아이돌",
                10L,
                "AUDIO",
                0,
                20,
                "popular",
                null
        );

        assertThat(response.maps()).hasSize(1);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();

        MapListResponse.MapSummaryResponse summary = response.maps().getFirst();
        assertThat(summary.mapId()).isEqualTo(100L);
        assertThat(summary.title()).isEqualTo("오디오 퀴즈");
        assertThat(summary.status()).isEqualTo("PUBLISHED");
        assertThat(summary.visibility()).isEqualTo("PUBLIC");
        assertThat(summary.category().categoryId()).isEqualTo(10L);
        assertThat(summary.category().name()).isEqualTo("음악");
        assertThat(summary.questionType()).isEqualTo("AUDIO");
        assertThat(summary.creator().userId()).isEqualTo(1L);
        assertThat(summary.creator().nickname()).isEqualTo("tester1");
        assertThat(summary.questionCount()).isEqualTo(1);
        assertThat(summary.playCount()).isEqualTo(135L);
        assertThat(summary.likeCount()).isEqualTo(12L);
        assertThat(summary.favoriteCount()).isEqualTo(4L);
        assertThat(summary.commentCount()).isEqualTo(5L);
        assertThat(summary.liked()).isFalse();
        assertThat(summary.favorited()).isFalse();

        ArgumentCaptor<List<MapStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<MapVisibility> visibilityCaptor = ArgumentCaptor.forClass(MapVisibility.class);
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> categoryIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<QuestionType> questionTypeCaptor = ArgumentCaptor.forClass(QuestionType.class);
        ArgumentCaptor<Long> creatorIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(quizMapRepository).searchMaps(
                statusesCaptor.capture(),
                visibilityCaptor.capture(),
                keywordCaptor.capture(),
                categoryIdCaptor.capture(),
                questionTypeCaptor.capture(),
                creatorIdCaptor.capture(),
                pageableCaptor.capture()
        );

        assertThat(statusesCaptor.getValue()).containsExactly(MapStatus.PUBLISHED);
        assertThat(visibilityCaptor.getValue()).isEqualTo(MapVisibility.PUBLIC);
        assertThat(keywordCaptor.getValue()).isEqualTo("아이돌");
        assertThat(categoryIdCaptor.getValue()).isEqualTo(10L);
        assertThat(questionTypeCaptor.getValue()).isEqualTo(QuestionType.AUDIO);
        assertThat(creatorIdCaptor.getValue()).isNull();
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMapsIncludesOwnNonDeletedMapsWhenCreatorIdIsCurrentUser() {
        given(quizMapRepository.searchMaps(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of()));

        mapService.getMaps(1L, null, null, null, 0, 20, "latest", 1L);

        ArgumentCaptor<List<MapStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<MapVisibility> visibilityCaptor = ArgumentCaptor.forClass(MapVisibility.class);
        ArgumentCaptor<Long> creatorIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(quizMapRepository).searchMaps(
                statusesCaptor.capture(),
                visibilityCaptor.capture(),
                any(),
                any(),
                any(),
                creatorIdCaptor.capture(),
                any(Pageable.class)
        );

        assertThat(statusesCaptor.getValue())
                .containsExactly(MapStatus.DRAFT, MapStatus.PROCESSING, MapStatus.PUBLISHED, MapStatus.BLOCKED);
        assertThat(visibilityCaptor.getValue()).isNull();
        assertThat(creatorIdCaptor.getValue()).isEqualTo(1L);
    }

    @Test
    void getMapsRejectsInvalidPageRequest() {
        assertThatThrownBy(() -> mapService.getMaps(null, null, null, null, -1, 20, "latest", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        assertThatThrownBy(() -> mapService.getMaps(null, null, null, null, 0, 101, "latest", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void getMapsRejectsUnsupportedSort() {
        assertThatThrownBy(() -> mapService.getMaps(null, null, null, null, 0, 20, "unknown", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void getMapEditorReturnsCreatorEditableMapDetails() {
        User creator = activeUser(1L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PROCESSING);
        Question question = question(200L, map);
        QuestionAnswer primaryAnswer = QuestionAnswer.create(question, "우주를 줄게", "우주를줄게", true);
        QuestionAnswer aliasAnswer = QuestionAnswer.create(question, "우주", "우주", false);
        QuestionMedia media = QuestionMedia.create(
                question,
                null,
                QuestionMediaSourceType.YOUTUBE,
                "https://youtube.com/watch?v=---",
                60000,
                102000,
                42000
        );
        ReflectionTestUtils.setField(media, "id", 300L);

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(100L, com.dogdog.nomat.domain.map.entity.QuestionStatus.ACTIVE))
                .willReturn(List.of(question));
        given(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscIdAsc(List.of(200L)))
                .willReturn(List.of(primaryAnswer, aliasAnswer));
        given(questionMediaRepository.findByQuestionIdIn(List.of(200L))).willReturn(List.of(media));

        MapEditorResponse response = mapService.getMapEditor(1L, 100L);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.visibility()).isEqualTo("PUBLIC");
        assertThat(response.title()).isEqualTo("오디오 퀴즈");
        assertThat(response.categoryId()).isEqualTo(10L);
        assertThat(response.questionType()).isEqualTo("AUDIO");
        assertThat(response.thumbnailAssetId()).isNull();
        assertThat(response.thumbnailUrl()).isNull();
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.questions()).hasSize(1);

        MapEditorResponse.QuestionEditorResponse questionResponse = response.questions().getFirst();
        assertThat(questionResponse.questionId()).isEqualTo(200L);
        assertThat(questionResponse.promptText()).isEqualTo("이 노래는?");
        assertThat(questionResponse.answers()).containsExactly("우주를 줄게", "우주");

        MapEditorResponse.MediaEditorResponse mediaResponse = questionResponse.media();
        assertThat(mediaResponse.mediaId()).isEqualTo(300L);
        assertThat(mediaResponse.sourceType()).isEqualTo("YOUTUBE");
        assertThat(mediaResponse.assetId()).isNull();
        assertThat(mediaResponse.sourceUrl()).isEqualTo("https://youtube.com/watch?v=---");
        assertThat(mediaResponse.startTimeMs()).isEqualTo(60000);
        assertThat(mediaResponse.endTimeMs()).isEqualTo(102000);
        assertThat(mediaResponse.durationMs()).isEqualTo(42000);
        assertThat(mediaResponse.processingStatus()).isEqualTo("PENDING");
        assertThat(mediaResponse.failureMessage()).isNull();
        assertThat(mediaResponse.audioUrl()).isNull();
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 10, 0));
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 10, 30));
    }

    @Test
    void getMapEditorRejectsOtherUsersMap() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));

        assertThatThrownBy(() -> mapService.getMapEditor(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_map_access");
    }

    @Test
    void getMapEditorRejectsUnknownMapId() {
        User creator = activeUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mapService.getMapEditor(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("map_or_question_not_found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createMapKeepsMapProcessingWithQuestionsAnswersAndYoutubeMedia() {
        User creator = activeUser(1L);
        Category category = category(10L);
        CreateMapRequest request = audioYoutubeMapRequest(null);

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));
        given(quizMapRepository.save(any(QuizMap.class))).willAnswer(invocation -> {
            QuizMap map = invocation.getArgument(0);
            ReflectionTestUtils.setField(map, "id", 100L);
            return map;
        });
        given(questionRepository.save(any(Question.class))).willAnswer(invocation -> invocation.getArgument(0));

        CreateMapResponse response = mapService.createMap(1L, request);

        assertThat(response.mapId()).isEqualTo(100L);

        ArgumentCaptor<QuizMap> mapCaptor = ArgumentCaptor.forClass(QuizMap.class);
        verify(quizMapRepository).save(mapCaptor.capture());
        QuizMap savedMap = mapCaptor.getValue();
        assertThat(savedMap.getCreator()).isEqualTo(creator);
        assertThat(savedMap.getCategory()).isEqualTo(category);
        assertThat(savedMap.getQuestionType()).isEqualTo(QuestionType.AUDIO);
        assertThat(savedMap.getTitle()).isEqualTo("20년대 아이돌 노래 맞히기");
        assertThat(savedMap.getDescription()).isEqualTo("20년대 아이돌 노래 맞히기입니다.");
        assertThat(savedMap.getStatus()).isEqualTo(MapStatus.PROCESSING);
        assertThat(savedMap.getVisibility()).isEqualTo(MapVisibility.PUBLIC);
        assertThat(savedMap.getQuestionCount()).isEqualTo(1);
        assertThat(savedMap.getPublishedAt()).isNull();

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(questionCaptor.capture());
        Question savedQuestion = questionCaptor.getValue();
        assertThat(savedQuestion.getMap()).isEqualTo(savedMap);
        assertThat(savedQuestion.getQuestionOrder()).isEqualTo(1);
        assertThat(savedQuestion.getPromptText()).isEqualTo("이 노래의 제목은 무엇인가요?");

        ArgumentCaptor<List<QuestionAnswer>> answersCaptor = ArgumentCaptor.forClass(List.class);
        verify(questionAnswerRepository).saveAll(answersCaptor.capture());
        List<QuestionAnswer> savedAnswers = answersCaptor.getValue();
        assertThat(savedAnswers).hasSize(3);
        assertThat(savedAnswers.getFirst().getAnswerText()).isEqualTo("우주를 줄게");
        assertThat(savedAnswers.getFirst().getAnswerKey()).isEqualTo("우주를줄게");
        assertThat(savedAnswers.getFirst().isPrimary()).isTrue();
        assertThat(savedAnswers.get(1).isPrimary()).isFalse();

        ArgumentCaptor<QuestionMedia> mediaCaptor = ArgumentCaptor.forClass(QuestionMedia.class);
        verify(questionMediaRepository).save(mediaCaptor.capture());
        QuestionMedia savedMedia = mediaCaptor.getValue();
        assertThat(savedMedia.getQuestion()).isEqualTo(savedQuestion);
        assertThat(savedMedia.getSourceType()).isEqualTo(QuestionMediaSourceType.YOUTUBE);
        assertThat(savedMedia.getSourceUrl()).isEqualTo("https://youtube.com/watch?v=---");
        assertThat(savedMedia.getStartTimeMs()).isEqualTo(60000);
        assertThat(savedMedia.getEndTimeMs()).isEqualTo(102000);
        assertThat(savedMedia.getDurationMs()).isEqualTo(42000);
        assertThat(savedMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.PENDING);

        ArgumentCaptor<AudioProcessingJob> jobCaptor = ArgumentCaptor.forClass(AudioProcessingJob.class);
        verify(audioProcessingJobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getQuestionMedia()).isEqualTo(savedMedia);
    }

    @Test
    void createMapAttachesThumbnailImageAsset() {
        User creator = activeUser(1L);
        Category category = category(10L);
        Asset thumbnail = imageAsset(20L, creator);
        CreateMapRequest request = audioYoutubeMapRequest(20L);

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));
        given(assetRepository.findById(20L)).willReturn(Optional.of(thumbnail));
        given(quizMapRepository.save(any(QuizMap.class))).willAnswer(invocation -> {
            QuizMap map = invocation.getArgument(0);
            ReflectionTestUtils.setField(map, "id", 100L);
            return map;
        });
        given(questionRepository.save(any(Question.class))).willAnswer(invocation -> invocation.getArgument(0));

        mapService.createMap(1L, request);

        ArgumentCaptor<QuizMap> mapCaptor = ArgumentCaptor.forClass(QuizMap.class);
        verify(quizMapRepository).save(mapCaptor.capture());
        assertThat(mapCaptor.getValue().getThumbnailAsset()).isEqualTo(thumbnail);
        assertThat(thumbnail.getStatus()).isEqualTo(AssetStatus.ATTACHED);
    }

    @Test
    void createMapRejectsUnknownUserId() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mapService.createMap(1L, audioYoutubeMapRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");

        verify(quizMapRepository, never()).save(any(QuizMap.class));
    }

    @Test
    void createMapRejectsInactiveCategory() {
        User creator = activeUser(1L);
        Category category = category(10L);
        ReflectionTestUtils.setField(category, "active", false);

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));

        assertThatThrownBy(() -> mapService.createMap(1L, audioYoutubeMapRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(quizMapRepository, never()).save(any(QuizMap.class));
    }

    @Test
    void createMapRejectsImageQuestionWithoutMedia() {
        User creator = activeUser(1L);
        Category category = category(10L);
        CreateMapRequest request = new CreateMapRequest(
                "이미지 퀴즈",
                10L,
                "IMAGE",
                null,
                "이미지 문제입니다.",
                "PUBLIC",
                List.of(new CreateMapRequest.QuestionRequest("이 이미지는 무엇인가요?", null, List.of("정답")))
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));

        assertThatThrownBy(() -> mapService.createMap(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(quizMapRepository, never()).save(any(QuizMap.class));
    }

    @Test
    void createMapRejectsDuplicateAnswerKeysInQuestion() {
        User creator = activeUser(1L);
        Category category = category(10L);
        CreateMapRequest request = new CreateMapRequest(
                "텍스트 퀴즈",
                10L,
                "TEXT",
                null,
                "텍스트 문제입니다.",
                "PUBLIC",
                List.of(new CreateMapRequest.QuestionRequest("정답은?", null, List.of("New Jeans", "newjeans")))
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));

        assertThatThrownBy(() -> mapService.createMap(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(quizMapRepository, never()).save(any(QuizMap.class));
    }

    @Test
    void createMapRejectsOtherUsersThumbnailAsset() {
        User creator = activeUser(1L);
        User otherUser = activeUser(2L);
        Category category = category(10L);
        Asset thumbnail = imageAsset(20L, otherUser);

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));
        given(assetRepository.findById(20L)).willReturn(Optional.of(thumbnail));

        assertThatThrownBy(() -> mapService.createMap(1L, audioYoutubeMapRequest(20L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(quizMapRepository, never()).save(any(QuizMap.class));
    }

    private CreateMapRequest audioYoutubeMapRequest(Long thumbnailAssetId) {
        return new CreateMapRequest(
                "20년대 아이돌 노래 맞히기",
                10L,
                "AUDIO",
                thumbnailAssetId,
                "20년대 아이돌 노래 맞히기입니다.",
                "PUBLIC",
                List.of(new CreateMapRequest.QuestionRequest(
                        "이 노래의 제목은 무엇인가요?",
                        new CreateMapRequest.MediaRequest(
                                "YOUTUBE",
                                null,
                                "https://youtube.com/watch?v=---",
                                60000L,
                                102000L
                        ),
                        List.of("우주를 줄게", "우주", "우줄")
                ))
        );
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

    private QuizMap quizMap(Long id, User creator, Category category, MapStatus status) {
        QuizMap map = QuizMap.create(
                creator,
                category,
                null,
                QuestionType.AUDIO,
                "오디오 퀴즈",
                "설명",
                MapVisibility.PUBLIC,
                1,
                status
        );
        ReflectionTestUtils.setField(map, "id", id);
        ReflectionTestUtils.setField(map, "createdAt", LocalDateTime.of(2026, 8, 11, 10, 0));
        ReflectionTestUtils.setField(map, "updatedAt", LocalDateTime.of(2026, 8, 11, 10, 30));
        return map;
    }

    private Question question(Long id, QuizMap map) {
        Question question = Question.create(map, 1, "이 노래는?");
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }

    private Asset imageAsset(Long id, User uploader) {
        Asset asset = Asset.createImage(
                uploader,
                "thumbnail.png",
                "uploads/images/2026/08/thumbnail.png",
                "https://cdn.nomat.com/uploads/images/2026/08/thumbnail.png",
                "image/png",
                1024L
        );
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }
}
