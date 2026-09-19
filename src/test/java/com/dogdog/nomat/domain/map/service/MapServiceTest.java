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
import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.map.dto.AudioProcessingRetryResponse;
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
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
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
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
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

    @Mock
    private MapLikeRepository mapLikeRepository;

    @Mock
    private MapFavoriteRepository mapFavoriteRepository;

    @Mock
    private AudioProcessingProperties audioProcessingProperties;

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
    void getMapReturnsDeletedCreatorAsAnonymousUser() {
        User creator = activeUser(1L);
        creator.delete();
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);

        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));

        MapDetailResponse response = mapService.getMap(null, 100L);

        assertThat(response.creator().userId()).isEqualTo(1L);
        assertThat(response.creator().nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(response.creator().profileImageUrl()).isNull();
        assertThat(response.creator().deleted()).isTrue();
    }

    @Test
    void getMapReturnsLikedStatusForAuthenticatedUser() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapLikeRepository.existsById(new MapLikeId(100L, 1L))).willReturn(true);

        MapDetailResponse response = mapService.getMap(1L, 100L);

        assertThat(response.liked()).isTrue();
        assertThat(response.favorited()).isFalse();
    }

    @Test
    void getMapReturnsFavoritedStatusForAuthenticatedUser() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapFavoriteRepository.existsById(new MapFavoriteId(100L, 1L))).willReturn(true);

        MapDetailResponse response = mapService.getMap(1L, 100L);

        assertThat(response.liked()).isFalse();
        assertThat(response.favorited()).isTrue();
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
    void getMapRejectsPublishedAudioMapWithUnreadyMedia() {
        QuizMap map = quizMap(100L, activeUser(1L), category(10L), MapStatus.PUBLISHED);
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(questionMediaRepository.existsByQuestionMapIdAndProcessingStatusNot(
                100L,
                QuestionMediaProcessingStatus.READY
        )).willReturn(true);

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
        assertThat(summary.creator().deleted()).isFalse();
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
    void getMapsReturnsDeletedCreatorsAsAnonymousUsers() {
        User creator = activeUser(1L);
        creator.delete();
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);

        given(quizMapRepository.searchMaps(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(map)));

        MapListResponse response = mapService.getMaps(null, null, null, null, 0, 20, "latest", null);

        MapListResponse.MapSummaryResponse summary = response.maps().getFirst();
        assertThat(summary.creator().userId()).isEqualTo(1L);
        assertThat(summary.creator().nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(summary.creator().profileImageUrl()).isNull();
        assertThat(summary.creator().deleted()).isTrue();
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
                .containsExactly(
                        MapStatus.DRAFT,
                        MapStatus.PROCESSING,
                        MapStatus.PROCESSING_FAILED,
                        MapStatus.PUBLISHED,
                        MapStatus.BLOCKED
                );
        assertThat(visibilityCaptor.getValue()).isNull();
        assertThat(creatorIdCaptor.getValue()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMapsReturnsAudioProcessingSummaryForOwnAudioMaps() {
        User creator = activeUser(1L);
        QuizMap map = quizMap(100L, creator, category(10L), MapStatus.PROCESSING);
        Question queuedQuestion = question(200L, map);
        Question processingQuestion = question(201L, map);
        Question failedQuestion = question(202L, map);
        QuestionMedia queuedMedia = youtubeMedia(queuedQuestion);
        QuestionMedia processingMedia = youtubeMedia(processingQuestion);
        QuestionMedia failedMedia = youtubeMedia(failedQuestion);
        processingMedia.startProcessing();
        failedMedia.failProcessing(AudioProcessingFailureCode.STORAGE_ERROR);

        LocalDateTime firstRequestedAt = LocalDateTime.of(2026, 8, 11, 9, 0);
        ReflectionTestUtils.setField(queuedMedia, "processingRequestedAt", firstRequestedAt);
        ReflectionTestUtils.setField(processingMedia, "processingRequestedAt", firstRequestedAt.plusMinutes(1));
        ReflectionTestUtils.setField(failedMedia, "processingRequestedAt", firstRequestedAt.plusMinutes(2));
        ReflectionTestUtils.setField(queuedMedia, "updatedAt", firstRequestedAt.plusMinutes(3));
        ReflectionTestUtils.setField(processingMedia, "updatedAt", firstRequestedAt.plusMinutes(4));
        ReflectionTestUtils.setField(failedMedia, "updatedAt", firstRequestedAt.plusMinutes(5));

        given(quizMapRepository.searchMaps(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(map)));
        given(questionMediaRepository.findByQuestionMapIdIn(List.of(100L)))
                .willReturn(List.of(queuedMedia, processingMedia, failedMedia));
        given(audioProcessingProperties.getDelayedThresholdMinutes()).willReturn(30L);

        MapListResponse response = mapService.getMaps(
                1L,
                null,
                null,
                null,
                0,
                20,
                "latest",
                1L
        );

        MapListResponse.AudioProcessingSummaryResponse summary = response.maps().getFirst().audioProcessing();
        assertThat(summary.status()).isEqualTo("FAILED");
        assertThat(summary.totalCount()).isEqualTo(3);
        assertThat(summary.queuedCount()).isEqualTo(1);
        assertThat(summary.processingCount()).isEqualTo(1);
        assertThat(summary.retryingCount()).isZero();
        assertThat(summary.readyCount()).isZero();
        assertThat(summary.failedCount()).isEqualTo(1);
        assertThat(summary.retryableFailedCount()).isEqualTo(1);
        assertThat(summary.sourceFailureCount()).isZero();
        assertThat(summary.canRetryAll()).isTrue();
        assertThat(summary.requestedAt()).isEqualTo(firstRequestedAt);
        assertThat(summary.lastUpdatedAt()).isEqualTo(firstRequestedAt.plusMinutes(5));
        verify(questionMediaRepository).findByQuestionMapIdIn(List.of(100L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMapsReturnsDelayedWhenOwnAudioMapHasWaitedPastThreshold() {
        User creator = activeUser(1L);
        QuizMap map = quizMap(100L, creator, category(10L), MapStatus.PROCESSING);
        QuestionMedia queuedMedia = youtubeMedia(question(200L, map));
        ReflectionTestUtils.setField(
                queuedMedia,
                "processingRequestedAt",
                LocalDateTime.now().minusMinutes(31)
        );

        given(quizMapRepository.searchMaps(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(map)));
        given(questionMediaRepository.findByQuestionMapIdIn(List.of(100L)))
                .willReturn(List.of(queuedMedia));
        given(audioProcessingProperties.getDelayedThresholdMinutes()).willReturn(30L);

        MapListResponse response = mapService.getMaps(
                1L,
                null,
                null,
                null,
                0,
                20,
                "latest",
                1L
        );

        MapListResponse.AudioProcessingSummaryResponse summary = response.maps().getFirst().audioProcessing();
        assertThat(summary.status()).isEqualTo("DELAYED");
        assertThat(summary.queuedCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMapsReturnsLikedStatusForAuthenticatedUser() {
        User creator = activeUser(1L);
        Category category = category(10L);
        QuizMap likedMap = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        QuizMap notLikedMap = quizMap(101L, creator, category, MapStatus.PUBLISHED);

        given(quizMapRepository.searchMaps(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(likedMap, notLikedMap)));
        given(mapLikeRepository.findMapIdsByUserIdAndMapIdIn(2L, List.of(100L, 101L)))
                .willReturn(List.of(100L));
        given(mapFavoriteRepository.findMapIdsByUserIdAndMapIdIn(2L, List.of(100L, 101L)))
                .willReturn(List.of());

        MapListResponse response = mapService.getMaps(2L, null, null, null, 0, 20, "latest", null);

        assertThat(response.maps()).hasSize(2);
        assertThat(response.maps().get(0).liked()).isTrue();
        assertThat(response.maps().get(1).liked()).isFalse();
        assertThat(response.maps().get(0).favorited()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMapsReturnsFavoritedStatusForAuthenticatedUser() {
        User creator = activeUser(1L);
        Category category = category(10L);
        QuizMap favoritedMap = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        QuizMap notFavoritedMap = quizMap(101L, creator, category, MapStatus.PUBLISHED);

        given(quizMapRepository.searchMaps(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(favoritedMap, notFavoritedMap)));
        given(mapLikeRepository.findMapIdsByUserIdAndMapIdIn(2L, List.of(100L, 101L)))
                .willReturn(List.of());
        given(mapFavoriteRepository.findMapIdsByUserIdAndMapIdIn(2L, List.of(100L, 101L)))
                .willReturn(List.of(100L));

        MapListResponse response = mapService.getMaps(2L, null, null, null, 0, 20, "latest", null);

        assertThat(response.maps()).hasSize(2);
        assertThat(response.maps().get(0).favorited()).isTrue();
        assertThat(response.maps().get(1).favorited()).isFalse();
        assertThat(response.maps().get(0).liked()).isFalse();
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
        assertThat(mediaResponse.processingStatus()).isEqualTo("QUEUED");
        assertThat(mediaResponse.failureCode()).isNull();
        assertThat(mediaResponse.failureType()).isNull();
        assertThat(mediaResponse.failureMessage()).isNull();
        assertThat(mediaResponse.canRetry()).isFalse();
        assertThat(mediaResponse.audioUrl()).isNull();
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 10, 0));
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 10, 30));

        media.failProcessing(AudioProcessingFailureCode.STORAGE_ERROR);
        MapEditorResponse failedResponse = mapService.getMapEditor(1L, 100L);
        MapEditorResponse.MediaEditorResponse failedMedia = failedResponse.questions().getFirst().media();

        assertThat(failedMedia.processingStatus()).isEqualTo("FAILED");
        assertThat(failedMedia.failureCode()).isEqualTo("STORAGE_ERROR");
        assertThat(failedMedia.failureType()).isEqualTo("SERVER");
        assertThat(failedMedia.failureMessage())
                .isEqualTo(AudioProcessingFailureCode.STORAGE_ERROR.getUserMessage())
                .doesNotContain("S3", "SDK", "exception");
        assertThat(failedMedia.canRetry()).isTrue();
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
    void saveMapDraftStoresIncompleteDraftWithoutRequiredMapFields() {
        User creator = activeUser(1L);
        SaveMapDraftRequest request = new SaveMapDraftRequest(
                null,
                null,
                null,
                null,
                "임시 메모",
                null,
                null
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.save(any(QuizMap.class))).willAnswer(invocation -> {
            QuizMap map = invocation.getArgument(0);
            ReflectionTestUtils.setField(map, "id", 100L);
            return map;
        });

        SaveMapDraftResponse response = mapService.saveMapDraft(1L, request);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.savedAt()).isNotNull();

        ArgumentCaptor<QuizMap> mapCaptor = ArgumentCaptor.forClass(QuizMap.class);
        verify(quizMapRepository).save(mapCaptor.capture());
        QuizMap savedMap = mapCaptor.getValue();
        assertThat(savedMap.getCreator()).isEqualTo(creator);
        assertThat(savedMap.getCategory()).isNull();
        assertThat(savedMap.getQuestionType()).isNull();
        assertThat(savedMap.getTitle()).isNull();
        assertThat(savedMap.getDescription()).isEqualTo("임시 메모");
        assertThat(savedMap.getStatus()).isEqualTo(MapStatus.DRAFT);
        assertThat(savedMap.getVisibility()).isEqualTo(MapVisibility.PRIVATE);
        assertThat(savedMap.getQuestionCount()).isZero();
        assertThat(savedMap.getPublishedAt()).isNull();

        verify(questionRepository, never()).save(any(Question.class));
        verify(audioProcessingJobRepository, never()).save(any(AudioProcessingJob.class));
    }

    @Test
    void saveMapDraftRejectsUserWithoutVerifiedEmail() {
        User creator = unverifiedUser(1L);
        SaveMapDraftRequest request = new SaveMapDraftRequest(null, null, null, null, null, null, null);
        given(userRepository.findById(1L)).willReturn(Optional.of(creator));

        assertThatThrownBy(() -> mapService.saveMapDraft(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email_verification_required");

        verify(quizMapRepository, never()).save(any(QuizMap.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveMapDraftStoresPartialQuestionAndYoutubeMediaWithoutProcessingJob() {
        User creator = activeUser(1L);
        Category category = category(10L);
        SaveMapDraftRequest request = new SaveMapDraftRequest(
                "작성 중인 오디오 퀴즈",
                10L,
                "AUDIO",
                null,
                null,
                "PRIVATE",
                List.of(new SaveMapDraftRequest.QuestionRequest(
                        null,
                        new SaveMapDraftRequest.MediaRequest(
                                "YOUTUBE",
                                null,
                                "https://youtube.com/watch?v=draft",
                                1000L,
                                null
                        ),
                        List.of("", "정답")
                ))
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));
        given(quizMapRepository.save(any(QuizMap.class))).willAnswer(invocation -> {
            QuizMap map = invocation.getArgument(0);
            ReflectionTestUtils.setField(map, "id", 100L);
            return map;
        });
        given(questionRepository.save(any(Question.class))).willAnswer(invocation -> invocation.getArgument(0));

        mapService.saveMapDraft(1L, request);

        ArgumentCaptor<QuizMap> mapCaptor = ArgumentCaptor.forClass(QuizMap.class);
        verify(quizMapRepository).save(mapCaptor.capture());
        QuizMap savedMap = mapCaptor.getValue();
        assertThat(savedMap.getCategory()).isEqualTo(category);
        assertThat(savedMap.getQuestionType()).isEqualTo(QuestionType.AUDIO);
        assertThat(savedMap.getStatus()).isEqualTo(MapStatus.DRAFT);
        assertThat(savedMap.getQuestionCount()).isEqualTo(1);

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(questionCaptor.capture());
        Question savedQuestion = questionCaptor.getValue();
        assertThat(savedQuestion.getMap()).isEqualTo(savedMap);
        assertThat(savedQuestion.getPromptText()).isNull();

        ArgumentCaptor<List<QuestionAnswer>> answersCaptor = ArgumentCaptor.forClass(List.class);
        verify(questionAnswerRepository).saveAll(answersCaptor.capture());
        List<QuestionAnswer> savedAnswers = answersCaptor.getValue();
        assertThat(savedAnswers).hasSize(1);
        assertThat(savedAnswers.getFirst().getAnswerText()).isEqualTo("정답");
        assertThat(savedAnswers.getFirst().isPrimary()).isTrue();

        ArgumentCaptor<QuestionMedia> mediaCaptor = ArgumentCaptor.forClass(QuestionMedia.class);
        verify(questionMediaRepository).save(mediaCaptor.capture());
        QuestionMedia savedMedia = mediaCaptor.getValue();
        assertThat(savedMedia.getSourceType()).isEqualTo(QuestionMediaSourceType.YOUTUBE);
        assertThat(savedMedia.getSourceUrl()).isEqualTo("https://www.youtube.com/watch?v=draft");
        assertThat(savedMedia.getStartTimeMs()).isEqualTo(1000);
        assertThat(savedMedia.getEndTimeMs()).isNull();
        assertThat(savedMedia.getDurationMs()).isNull();
        assertThat(savedMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.QUEUED);

        verify(audioProcessingJobRepository, never()).save(any(AudioProcessingJob.class));
    }

    @Test
    void saveMapDraftRejectsInvalidYoutubeTimeRange() {
        User creator = activeUser(1L);
        SaveMapDraftRequest request = new SaveMapDraftRequest(
                null,
                null,
                "AUDIO",
                null,
                null,
                null,
                List.of(new SaveMapDraftRequest.QuestionRequest(
                        null,
                        new SaveMapDraftRequest.MediaRequest(
                                "YOUTUBE",
                                null,
                                "https://youtube.com/watch?v=draft",
                                2000L,
                                1000L
                        ),
                        null
                ))
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));

        assertThatThrownBy(() -> mapService.saveMapDraft(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_youtube_clip_range");

        verify(quizMapRepository, never()).save(any(QuizMap.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void modifyMapPublishesCompletedDraftWithCreatedTextQuestion() {
        User creator = activeUser(1L);
        Category category = category(10L);
        QuizMap map = QuizMap.draft(
                creator,
                null,
                null,
                null,
                null,
                null,
                MapVisibility.PRIVATE,
                0
        );
        ReflectionTestUtils.setField(map, "id", 100L);
        ReflectionTestUtils.setField(map, "createdAt", LocalDateTime.of(2026, 8, 11, 10, 0));
        ReflectionTestUtils.setField(map, "updatedAt", LocalDateTime.of(2026, 8, 11, 10, 30));
        ModifyMapRequest request = new ModifyMapRequest(
                1,
                Map.of(
                        "title", "텍스트 퀴즈",
                        "categoryId", 10L,
                        "questionType", "TEXT",
                        "visibility", "PUBLIC"
                ),
                new ModifyMapRequest.QuestionsRequest(
                        List.of(new ModifyMapRequest.CreateQuestionRequest(
                                "temp-1",
                                "정답은?",
                                null,
                                List.of("정답")
                        )),
                        null,
                        null
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));
        given(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(100L, QuestionStatus.ACTIVE))
                .willReturn(List.of());
        given(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscIdAsc(List.of()))
                .willReturn(List.of());
        given(questionMediaRepository.findByQuestionIdIn(List.of())).willReturn(List.of());
        given(questionRepository.save(any(Question.class))).willAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            ReflectionTestUtils.setField(question, "id", 200L);
            return question;
        });

        ModifyMapResponse response = mapService.modifyMap(1L, 100L, request);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.version()).isEqualTo(2);
        assertThat(response.createdQuestions()).hasSize(1);
        assertThat(response.createdQuestions().getFirst().clientId()).isEqualTo("temp-1");
        assertThat(response.createdQuestions().getFirst().questionId()).isEqualTo(200L);
        assertThat(map.getTitle()).isEqualTo("텍스트 퀴즈");
        assertThat(map.getCategory()).isEqualTo(category);
        assertThat(map.getQuestionType()).isEqualTo(QuestionType.TEXT);
        assertThat(map.getVisibility()).isEqualTo(MapVisibility.PUBLIC);
        assertThat(map.getQuestionCount()).isEqualTo(1);
        assertThat(map.getStatus()).isEqualTo(MapStatus.PUBLISHED);
        assertThat(map.getPublishedAt()).isNotNull();

        ArgumentCaptor<List<QuestionAnswer>> answersCaptor = ArgumentCaptor.forClass(List.class);
        verify(questionAnswerRepository).saveAll(answersCaptor.capture());
        assertThat(answersCaptor.getValue()).hasSize(1);
        assertThat(answersCaptor.getValue().getFirst().getAnswerText()).isEqualTo("정답");
        verify(questionMediaRepository, never()).save(any(QuestionMedia.class));
        verify(audioProcessingJobRepository, never()).save(any(AudioProcessingJob.class));
    }

    @Test
    void modifyMapRejectsVersionConflict() {
        User creator = activeUser(1L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "version", 4);
        ModifyMapRequest request = new ModifyMapRequest(3, null, null);

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));

        assertThatThrownBy(() -> mapService.modifyMap(1L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("map_version_conflict");

        verify(questionRepository, never()).findByMapIdAndStatusOrderByQuestionOrderAsc(any(), any());
    }

    @Test
    void modifyMapRejectsUnverifiedUserDraft() {
        User creator = unverifiedUser(1L);
        QuizMap map = QuizMap.draft(
                creator,
                null,
                null,
                null,
                null,
                null,
                MapVisibility.PRIVATE,
                0
        );
        ReflectionTestUtils.setField(map, "id", 100L);
        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));

        assertThatThrownBy(() -> mapService.modifyMap(1L, 100L, new ModifyMapRequest(1, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email_verification_required");

        verify(questionRepository, never()).findByMapIdAndStatusOrderByQuestionOrderAsc(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void modifyPublishedMapAllowsUserWithoutVerifiedEmail() {
        User creator = unverifiedUser(1L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        Question question = question(200L, map);
        QuestionAnswer oldAnswer = QuestionAnswer.create(question, "이전 정답", "이전정답", true);
        ModifyMapRequest request = new ModifyMapRequest(
                1,
                null,
                new ModifyMapRequest.QuestionsRequest(
                        null,
                        List.of(new ModifyMapRequest.UpdateQuestionRequest(
                                200L,
                                "수정된 문제",
                                new ModifyMapRequest.MediaRequest(
                                        "YOUTUBE",
                                        null,
                                        "https://youtube.com/watch?v=updated",
                                        60000L,
                                        80000L
                                ),
                                List.of("수정 정답", "수정")
                        )),
                        null
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(100L, QuestionStatus.ACTIVE))
                .willReturn(List.of(question));
        given(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscIdAsc(List.of(200L)))
                .willReturn(List.of(oldAnswer));
        given(questionMediaRepository.findByQuestionIdIn(List.of(200L))).willReturn(List.of());
        given(questionMediaRepository.save(any(QuestionMedia.class))).willAnswer(invocation -> invocation.getArgument(0));

        ModifyMapResponse response = mapService.modifyMap(1L, 100L, request);

        assertThat(response.version()).isEqualTo(2);
        assertThat(question.getPromptText()).isEqualTo("수정된 문제");
        assertThat(map.getStatus()).isEqualTo(MapStatus.PROCESSING);
        assertThat(map.getQuestionCount()).isEqualTo(1);

        ArgumentCaptor<List<QuestionAnswer>> answersCaptor = ArgumentCaptor.forClass(List.class);
        verify(questionAnswerRepository).saveAll(answersCaptor.capture());
        List<QuestionAnswer> savedAnswers = answersCaptor.getValue();
        assertThat(savedAnswers).hasSize(2);
        assertThat(savedAnswers.getFirst().getAnswerText()).isEqualTo("수정 정답");
        assertThat(savedAnswers.getFirst().isPrimary()).isTrue();
        verify(questionAnswerRepository).deleteByQuestionId(200L);
        InOrder answerUpdateOrder = Mockito.inOrder(questionAnswerRepository);
        answerUpdateOrder.verify(questionAnswerRepository).deleteByQuestionId(200L);
        answerUpdateOrder.verify(questionAnswerRepository).flush();
        answerUpdateOrder.verify(questionAnswerRepository).saveAll(any());

        ArgumentCaptor<QuestionMedia> mediaCaptor = ArgumentCaptor.forClass(QuestionMedia.class);
        verify(questionMediaRepository).save(mediaCaptor.capture());
        QuestionMedia savedMedia = mediaCaptor.getValue();
        assertThat(savedMedia.getSourceType()).isEqualTo(QuestionMediaSourceType.YOUTUBE);
        assertThat(savedMedia.getSourceUrl()).isEqualTo("https://www.youtube.com/watch?v=updated");
        assertThat(savedMedia.getStartTimeMs()).isEqualTo(60000);
        assertThat(savedMedia.getEndTimeMs()).isEqualTo(80000);
        assertThat(savedMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.QUEUED);

        ArgumentCaptor<AudioProcessingJob> jobCaptor = ArgumentCaptor.forClass(AudioProcessingJob.class);
        verify(audioProcessingJobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getQuestionMedia()).isEqualTo(savedMedia);
    }

    @Test
    @SuppressWarnings("unchecked")
    void modifyMapDoesNotReprocessYoutubeMediaWhenOnlyAnswersChanged() {
        User creator = activeUser(1L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        Question question = question(200L, map);
        QuestionAnswer oldAnswer = QuestionAnswer.create(question, "이전 정답", "이전정답", true);
        QuestionMedia existingMedia = QuestionMedia.create(
                question,
                null,
                QuestionMediaSourceType.YOUTUBE,
                "https://www.youtube.com/watch?v=updated",
                60000,
                80000,
                20000
        );
        Asset audioAsset = Asset.createAudio(
                creator,
                "question-media-200.mp3",
                "uploads/audios/2026/09/question-media-200.mp3",
                "https://cdn.nomat.com/uploads/audios/2026/09/question-media-200.mp3",
                "audio/mpeg",
                1234L,
                20000
        );
        ReflectionTestUtils.setField(audioAsset, "id", 30L);
        audioAsset.attach();
        existingMedia.completeProcessing(audioAsset, 20000);
        ModifyMapRequest request = new ModifyMapRequest(
                1,
                null,
                new ModifyMapRequest.QuestionsRequest(
                        null,
                        List.of(new ModifyMapRequest.UpdateQuestionRequest(
                                200L,
                                "수정된 문제",
                                new ModifyMapRequest.MediaRequest(
                                        "YOUTUBE",
                                        null,
                                        "https://youtube.com/watch?v=updated",
                                        60000L,
                                        80000L
                                ),
                                List.of("새 정답", "추가 정답")
                        )),
                        null
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(100L, QuestionStatus.ACTIVE))
                .willReturn(List.of(question));
        given(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscIdAsc(List.of(200L)))
                .willReturn(List.of(oldAnswer));
        given(questionMediaRepository.findByQuestionIdIn(List.of(200L))).willReturn(List.of(existingMedia));

        mapService.modifyMap(1L, 100L, request);

        verify(audioProcessingJobRepository, never()).findByQuestionMediaId(any());
        verify(audioProcessingJobRepository, never()).save(any(AudioProcessingJob.class));
        assertThat(existingMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.READY);
    }

    @Test
    void modifyMapRequiresUrlChangeForUnavailableSourceFailure() {
        User creator = activeUser(1L);
        QuizMap map = quizMap(100L, creator, category(10L), MapStatus.PROCESSING);
        Question question = question(200L, map);
        QuestionAnswer answer = QuestionAnswer.create(question, "정답", "정답", true);
        QuestionMedia media = youtubeMedia(question);
        ReflectionTestUtils.setField(media, "id", 300L);
        media.failProcessing(AudioProcessingFailureCode.SOURCE_UNAVAILABLE);
        ModifyMapRequest request = youtubeQuestionUpdateRequest(
                200L,
                "https://youtube.com/watch?v=---",
                60_000L,
                103_000L
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(100L, QuestionStatus.ACTIVE))
                .willReturn(List.of(question));
        given(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscIdAsc(List.of(200L)))
                .willReturn(List.of(answer));
        given(questionMediaRepository.findByQuestionIdIn(List.of(200L))).willReturn(List.of(media));

        assertThatThrownBy(() -> mapService.modifyMap(1L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("audio_source_failure_not_resolved");

        assertThat(media.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.FAILED);
        assertThat(media.getFailureCode()).isEqualTo(AudioProcessingFailureCode.SOURCE_UNAVAILABLE);
        verify(audioProcessingJobRepository, never()).findByQuestionMediaId(any());
    }

    @Test
    void modifyMapRetriesServerFailuresWhenLastSourceFailureIsResolved() {
        User creator = activeUser(1L);
        QuizMap map = quizMap(100L, creator, category(10L), MapStatus.PROCESSING_FAILED);
        Question question = question(200L, map);
        Question serverQuestion = question(201L, map);
        QuestionAnswer answer = QuestionAnswer.create(question, "정답", "정답", true);
        QuestionAnswer serverAnswer = QuestionAnswer.create(serverQuestion, "서버 정답", "서버정답", true);
        QuestionMedia media = youtubeMedia(question);
        QuestionMedia serverMedia = youtubeMedia(serverQuestion);
        ReflectionTestUtils.setField(media, "id", 300L);
        ReflectionTestUtils.setField(serverMedia, "id", 301L);
        AudioProcessingJob job = AudioProcessingJob.create(media);
        AudioProcessingJob serverJob = AudioProcessingJob.create(serverMedia);
        job.start();
        job.fail(AudioProcessingFailureCode.INVALID_AUDIO_RANGE, "invalid duration");
        serverJob.start();
        serverJob.fail(AudioProcessingFailureCode.UNKNOWN, "unexpected exception");
        LocalDateTime previousRequestedAt = media.getProcessingRequestedAt();
        ModifyMapRequest request = youtubeQuestionUpdateRequest(
                200L,
                "https://youtube.com/watch?v=---",
                60_000L,
                103_000L
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(100L, QuestionStatus.ACTIVE))
                .willReturn(List.of(question, serverQuestion));
        given(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscIdAsc(List.of(200L, 201L)))
                .willReturn(List.of(answer, serverAnswer));
        given(questionMediaRepository.findByQuestionIdIn(List.of(200L, 201L)))
                .willReturn(List.of(media, serverMedia));
        given(audioProcessingJobRepository.findByQuestionMediaId(300L)).willReturn(Optional.of(job));
        given(audioProcessingJobRepository.findByQuestionMediaQuestionMapIdAndStatusOrderByIdAsc(
                100L,
                AudioProcessingJobStatus.FAILED
        )).willReturn(List.of(serverJob));

        mapService.modifyMap(1L, 100L, request);

        assertThat(media.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.QUEUED);
        assertThat(media.getFailureCode()).isNull();
        assertThat(media.getEndTimeMs()).isEqualTo(103_000);
        assertThat(media.getProcessingRequestedAt()).isAfterOrEqualTo(previousRequestedAt);
        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.QUEUED);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(serverJob.getStatus()).isEqualTo(AudioProcessingJobStatus.QUEUED);
        assertThat(serverJob.getAttemptCount()).isZero();
        assertThat(serverMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.QUEUED);
        assertThat(map.getStatus()).isEqualTo(MapStatus.PROCESSING);
    }

    @Test
    void retryAudioProcessingQueuesAllFinalServerFailuresAsOneRequest() {
        User creator = activeUser(1L);
        QuizMap map = quizMap(100L, creator, category(10L), MapStatus.PROCESSING_FAILED);
        Question storageQuestion = question(200L, map);
        Question unknownQuestion = question(201L, map);
        QuestionMedia storageMedia = youtubeMedia(storageQuestion);
        QuestionMedia unknownMedia = youtubeMedia(unknownQuestion);
        ReflectionTestUtils.setField(storageMedia, "id", 300L);
        ReflectionTestUtils.setField(unknownMedia, "id", 301L);
        LocalDateTime previousRequestedAt = LocalDateTime.now().minusHours(1);
        ReflectionTestUtils.setField(storageMedia, "processingRequestedAt", previousRequestedAt);
        ReflectionTestUtils.setField(unknownMedia, "processingRequestedAt", previousRequestedAt);
        AudioProcessingJob storageJob = AudioProcessingJob.create(storageMedia);
        AudioProcessingJob unknownJob = AudioProcessingJob.create(unknownMedia);
        storageJob.start();
        storageJob.fail(AudioProcessingFailureCode.STORAGE_ERROR, "s3 client exception");
        unknownJob.start();
        unknownJob.fail(AudioProcessingFailureCode.UNKNOWN, "unexpected exception");

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(audioProcessingJobRepository.findByQuestionMediaQuestionMapIdAndStatusOrderByIdAsc(
                100L,
                AudioProcessingJobStatus.FAILED
        )).willReturn(List.of(storageJob, unknownJob));

        AudioProcessingRetryResponse response = mapService.retryAudioProcessing(1L, 100L);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.retriedQuestionIds()).containsExactly(200L, 201L);
        assertThat(response.retriedCount()).isEqualTo(2);
        assertThat(response.requestedAt()).isAfter(previousRequestedAt);
        assertThat(storageJob.getStatus()).isEqualTo(AudioProcessingJobStatus.QUEUED);
        assertThat(unknownJob.getStatus()).isEqualTo(AudioProcessingJobStatus.QUEUED);
        assertThat(storageJob.getAttemptCount()).isZero();
        assertThat(unknownJob.getAttemptCount()).isZero();
        assertThat(storageJob.getFailureCode()).isNull();
        assertThat(unknownJob.getFailureCode()).isNull();
        assertThat(storageJob.getStartedAt()).isNull();
        assertThat(storageJob.getCompletedAt()).isNull();
        assertThat(storageMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.QUEUED);
        assertThat(unknownMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.QUEUED);
        assertThat(storageMedia.getProcessingRequestedAt()).isEqualTo(response.requestedAt());
        assertThat(unknownMedia.getProcessingRequestedAt()).isEqualTo(response.requestedAt());
        assertThat(map.getStatus()).isEqualTo(MapStatus.PROCESSING);
    }

    @Test
    void retryAudioProcessingRejectsEntireRequestWhenSourceFailureRemains() {
        User creator = activeUser(1L);
        QuizMap map = quizMap(100L, creator, category(10L), MapStatus.PROCESSING);
        Question sourceQuestion = question(200L, map);
        Question serverQuestion = question(201L, map);
        QuestionMedia sourceMedia = youtubeMedia(sourceQuestion);
        QuestionMedia serverMedia = youtubeMedia(serverQuestion);
        AudioProcessingJob sourceJob = AudioProcessingJob.create(sourceMedia);
        AudioProcessingJob serverJob = AudioProcessingJob.create(serverMedia);
        sourceJob.start();
        sourceJob.fail(AudioProcessingFailureCode.SOURCE_UNAVAILABLE, "video unavailable");
        serverJob.start();
        serverJob.fail(AudioProcessingFailureCode.STORAGE_ERROR, "s3 client exception");

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(audioProcessingJobRepository.findByQuestionMediaQuestionMapIdAndStatusOrderByIdAsc(
                100L,
                AudioProcessingJobStatus.FAILED
        )).willReturn(List.of(sourceJob, serverJob));

        assertThatThrownBy(() -> mapService.retryAudioProcessing(1L, 100L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getMessageCode()).isEqualTo("audio_source_failures_must_be_fixed");
                    assertThat(exception.getData()).isEqualTo(Map.of("questionIds", List.of(200L)));
                });

        assertThat(sourceJob.getStatus()).isEqualTo(AudioProcessingJobStatus.FAILED);
        assertThat(serverJob.getStatus()).isEqualTo(AudioProcessingJobStatus.FAILED);
        assertThat(sourceMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.FAILED);
        assertThat(serverMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.FAILED);
    }

    @Test
    void retryAudioProcessingRejectsOtherUsersMapBeforeLoadingJobs() {
        User user = activeUser(1L);
        QuizMap map = quizMap(100L, activeUser(2L), category(10L), MapStatus.PROCESSING);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));

        assertThatThrownBy(() -> mapService.retryAudioProcessing(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_map_access");

        verify(audioProcessingJobRepository, never())
                .findByQuestionMediaQuestionMapIdAndStatusOrderByIdAsc(any(), any());
    }

    @Test
    void retryAudioProcessingRejectsWhenNoRetryableFailureRemains() {
        User creator = activeUser(1L);
        QuizMap map = quizMap(100L, creator, category(10L), MapStatus.PROCESSING);

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(audioProcessingJobRepository.findByQuestionMediaQuestionMapIdAndStatusOrderByIdAsc(
                100L,
                AudioProcessingJobStatus.FAILED
        )).willReturn(List.of());

        assertThatThrownBy(() -> mapService.retryAudioProcessing(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("audio_processing_retry_not_allowed");
    }

    @Test
    void retryAudioProcessingRejectsRepeatedRequestAfterJobsAreQueued() {
        User creator = activeUser(1L);
        QuizMap map = quizMap(100L, creator, category(10L), MapStatus.PROCESSING);
        QuestionMedia media = youtubeMedia(question(200L, map));
        AudioProcessingJob job = AudioProcessingJob.create(media);
        job.start();
        job.fail(AudioProcessingFailureCode.INTERNAL_ERROR, "unexpected server error");

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(audioProcessingJobRepository.findByQuestionMediaQuestionMapIdAndStatusOrderByIdAsc(
                100L,
                AudioProcessingJobStatus.FAILED
        )).willReturn(List.of(job), List.of());

        mapService.retryAudioProcessing(1L, 100L);

        assertThatThrownBy(() -> mapService.retryAudioProcessing(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("audio_processing_retry_not_allowed");
        assertThat(job.getStatus()).isEqualTo(AudioProcessingJobStatus.QUEUED);
    }

    @Test
    void modifyMapDeletesSourceFailureQuestionAndItsProcessingJob() {
        User creator = activeUser(1L);
        QuizMap map = quizMap(100L, creator, category(10L), MapStatus.PROCESSING);
        Question failedQuestion = question(200L, map);
        Question readyQuestion = question(201L, map);
        QuestionAnswer failedAnswer = QuestionAnswer.create(failedQuestion, "실패 정답", "실패정답", true);
        QuestionAnswer readyAnswer = QuestionAnswer.create(readyQuestion, "정상 정답", "정상정답", true);
        QuestionMedia failedMedia = youtubeMedia(failedQuestion);
        failedMedia.failProcessing(AudioProcessingFailureCode.SOURCE_UNAVAILABLE);
        QuestionMedia readyMedia = youtubeMedia(readyQuestion);
        Asset audioAsset = Asset.createAudio(
                creator,
                "ready.mp3",
                "uploads/audios/ready.mp3",
                "https://cdn.nomat.com/uploads/audios/ready.mp3",
                "audio/mpeg",
                1234L,
                42000
        );
        audioAsset.attach();
        readyMedia.completeProcessing(audioAsset, 42000);
        ModifyMapRequest request = new ModifyMapRequest(
                1,
                null,
                new ModifyMapRequest.QuestionsRequest(null, null, List.of(200L))
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));
        given(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(100L, QuestionStatus.ACTIVE))
                .willReturn(List.of(failedQuestion, readyQuestion));
        given(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscIdAsc(List.of(200L, 201L)))
                .willReturn(List.of(failedAnswer, readyAnswer));
        given(questionMediaRepository.findByQuestionIdIn(List.of(200L, 201L)))
                .willReturn(List.of(failedMedia, readyMedia));

        mapService.modifyMap(1L, 100L, request);

        verify(audioProcessingJobRepository).deleteByQuestionMediaQuestionId(200L);
        verify(questionMediaRepository).deleteByQuestionId(200L);
        assertThat(failedQuestion.getStatus()).isEqualTo(QuestionStatus.DELETED);
        assertThat(map.getQuestionCount()).isEqualTo(1);
        assertThat(map.getStatus()).isEqualTo(MapStatus.PUBLISHED);
    }

    @Test
    void deleteMapDeletesOwnMapAndPendingAudioJobs() {
        User creator = activeUser(1L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PROCESSING);

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));

        mapService.deleteMap(1L, 100L);

        assertThat(map.getStatus()).isEqualTo(MapStatus.DELETED);
        assertThat(map.getDeletedAt()).isNotNull();
        verify(audioProcessingJobRepository).deleteByQuestionMediaQuestionMapId(100L);
    }

    @Test
    void deleteMapRejectsOtherUsersMap() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.of(map));

        assertThatThrownBy(() -> mapService.deleteMap(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_map_access");

        assertThat(map.getStatus()).isEqualTo(MapStatus.PUBLISHED);
        verify(audioProcessingJobRepository, never()).deleteByQuestionMediaQuestionMapId(any());
    }

    @Test
    void deleteMapRejectsUnknownOrDeletedMap() {
        User creator = activeUser(1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(quizMapRepository.findByIdAndStatusNot(100L, MapStatus.DELETED)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mapService.deleteMap(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("map_not_found");

        verify(audioProcessingJobRepository, never()).deleteByQuestionMediaQuestionMapId(any());
    }

    @Test
    void likeMapCreatesLikeAndIncreasesLikeCount() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "likeCount", 12L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapLikeRepository.existsById(new MapLikeId(100L, 1L))).willReturn(false);

        MapLikeResponse response = mapService.likeMap(1L, 100L);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(13L);
        assertThat(map.getLikeCount()).isEqualTo(13L);

        ArgumentCaptor<MapLike> mapLikeCaptor = ArgumentCaptor.forClass(MapLike.class);
        verify(mapLikeRepository).save(mapLikeCaptor.capture());
        assertThat(mapLikeCaptor.getValue().getId()).isEqualTo(new MapLikeId(100L, 1L));
        assertThat(mapLikeCaptor.getValue().getMap()).isEqualTo(map);
        assertThat(mapLikeCaptor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void likeMapRejectsAlreadyLikedMap() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "likeCount", 12L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapLikeRepository.existsById(new MapLikeId(100L, 1L))).willReturn(true);

        assertThatThrownBy(() -> mapService.likeMap(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already_liked_map");

        assertThat(map.getLikeCount()).isEqualTo(12L);
        verify(mapLikeRepository, never()).save(any(MapLike.class));
    }

    @Test
    void unlikeMapDeletesLikeAndDecreasesLikeCount() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "likeCount", 12L);
        MapLike mapLike = MapLike.create(map, user);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapLikeRepository.findById(new MapLikeId(100L, 1L))).willReturn(Optional.of(mapLike));

        MapLikeResponse response = mapService.unlikeMap(1L, 100L);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(11L);
        assertThat(map.getLikeCount()).isEqualTo(11L);
        verify(mapLikeRepository).delete(mapLike);
    }

    @Test
    void unlikeMapReturnsSuccessWhenLikeDoesNotExist() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "likeCount", 12L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapLikeRepository.findById(new MapLikeId(100L, 1L))).willReturn(Optional.empty());

        MapLikeResponse response = mapService.unlikeMap(1L, 100L);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(12L);
        assertThat(map.getLikeCount()).isEqualTo(12L);
        verify(mapLikeRepository, never()).delete(any(MapLike.class));
    }

    @Test
    void favoriteMapCreatesFavoriteAndIncreasesFavoriteCount() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "favoriteCount", 4L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapFavoriteRepository.existsById(new MapFavoriteId(100L, 1L))).willReturn(false);

        MapFavoriteResponse response = mapService.favoriteMap(1L, 100L);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.favorited()).isTrue();
        assertThat(response.favoriteCount()).isEqualTo(5L);
        assertThat(map.getFavoriteCount()).isEqualTo(5L);

        ArgumentCaptor<MapFavorite> mapFavoriteCaptor = ArgumentCaptor.forClass(MapFavorite.class);
        verify(mapFavoriteRepository).save(mapFavoriteCaptor.capture());
        assertThat(mapFavoriteCaptor.getValue().getId()).isEqualTo(new MapFavoriteId(100L, 1L));
        assertThat(mapFavoriteCaptor.getValue().getMap()).isEqualTo(map);
        assertThat(mapFavoriteCaptor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void favoriteMapRejectsAlreadyFavoritedMap() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "favoriteCount", 4L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapFavoriteRepository.existsById(new MapFavoriteId(100L, 1L))).willReturn(true);

        assertThatThrownBy(() -> mapService.favoriteMap(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already_favorited_map");

        assertThat(map.getFavoriteCount()).isEqualTo(4L);
        verify(mapFavoriteRepository, never()).save(any(MapFavorite.class));
    }

    @Test
    void unfavoriteMapDeletesFavoriteAndDecreasesFavoriteCount() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "favoriteCount", 4L);
        MapFavorite mapFavorite = MapFavorite.create(map, user);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapFavoriteRepository.findById(new MapFavoriteId(100L, 1L))).willReturn(Optional.of(mapFavorite));

        MapFavoriteResponse response = mapService.unfavoriteMap(1L, 100L);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.favorited()).isFalse();
        assertThat(response.favoriteCount()).isEqualTo(3L);
        assertThat(map.getFavoriteCount()).isEqualTo(3L);
        verify(mapFavoriteRepository).delete(mapFavorite);
    }

    @Test
    void unfavoriteMapReturnsSuccessWhenFavoriteDoesNotExist() {
        User user = activeUser(1L);
        User creator = activeUser(2L);
        Category category = category(10L);
        QuizMap map = quizMap(100L, creator, category, MapStatus.PUBLISHED);
        ReflectionTestUtils.setField(map, "favoriteCount", 4L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(quizMapRepository.findByIdAndStatusAndVisibility(100L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(mapFavoriteRepository.findById(new MapFavoriteId(100L, 1L))).willReturn(Optional.empty());

        MapFavoriteResponse response = mapService.unfavoriteMap(1L, 100L);

        assertThat(response.mapId()).isEqualTo(100L);
        assertThat(response.favorited()).isFalse();
        assertThat(response.favoriteCount()).isEqualTo(4L);
        assertThat(map.getFavoriteCount()).isEqualTo(4L);
        verify(mapFavoriteRepository, never()).delete(any(MapFavorite.class));
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
        assertThat(savedMedia.getSourceUrl()).isEqualTo("https://www.youtube.com/watch?v=---");
        assertThat(savedMedia.getStartTimeMs()).isEqualTo(60000);
        assertThat(savedMedia.getEndTimeMs()).isEqualTo(102000);
        assertThat(savedMedia.getDurationMs()).isEqualTo(42000);
        assertThat(savedMedia.getProcessingStatus()).isEqualTo(QuestionMediaProcessingStatus.QUEUED);

        ArgumentCaptor<AudioProcessingJob> jobCaptor = ArgumentCaptor.forClass(AudioProcessingJob.class);
        verify(audioProcessingJobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getQuestionMedia()).isEqualTo(savedMedia);
    }

    @Test
    void createMapAcceptsYoutubeShortsUrlAndStoresCanonicalUrl() {
        User creator = activeUser(1L);
        Category category = category(10L);
        CreateMapRequest request = audioYoutubeMapRequestWithUrl(" https://www.youtube.com/shorts/abc123 ");

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));
        given(quizMapRepository.save(any(QuizMap.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(questionRepository.save(any(Question.class))).willAnswer(invocation -> invocation.getArgument(0));

        mapService.createMap(1L, request);

        ArgumentCaptor<QuestionMedia> mediaCaptor = ArgumentCaptor.forClass(QuestionMedia.class);
        verify(questionMediaRepository).save(mediaCaptor.capture());
        assertThat(mediaCaptor.getValue().getSourceUrl()).isEqualTo("https://www.youtube.com/watch?v=abc123");
    }

    @Test
    void createMapAcceptsYoutuBeUrl() {
        User creator = activeUser(1L);
        Category category = category(10L);
        CreateMapRequest request = audioYoutubeMapRequestWithUrl("https://youtu.be/abc123");

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));
        given(quizMapRepository.save(any(QuizMap.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(questionRepository.save(any(Question.class))).willAnswer(invocation -> invocation.getArgument(0));

        mapService.createMap(1L, request);

        ArgumentCaptor<QuestionMedia> mediaCaptor = ArgumentCaptor.forClass(QuestionMedia.class);
        verify(questionMediaRepository).save(mediaCaptor.capture());
        assertThat(mediaCaptor.getValue().getSourceUrl()).isEqualTo("https://www.youtube.com/watch?v=abc123");
    }

    @Test
    void createMapRemovesPlaylistParametersFromYoutubeUrl() {
        User creator = activeUser(1L);
        Category category = category(10L);
        CreateMapRequest request = audioYoutubeMapRequestWithUrl(
                "https://www.youtube.com/watch?v=W_QQ2VF1b4Y&list=PLGoTZQa91pYvSdHpes7H6fit_iOYtH-fj&index=3&t=18543s"
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));
        given(quizMapRepository.save(any(QuizMap.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(questionRepository.save(any(Question.class))).willAnswer(invocation -> invocation.getArgument(0));

        mapService.createMap(1L, request);

        ArgumentCaptor<QuestionMedia> mediaCaptor = ArgumentCaptor.forClass(QuestionMedia.class);
        verify(questionMediaRepository).save(mediaCaptor.capture());
        assertThat(mediaCaptor.getValue().getSourceUrl()).isEqualTo("https://www.youtube.com/watch?v=W_QQ2VF1b4Y");
    }

    @Test
    void createMapRejectsUnsupportedYoutubeUrl() {
        User creator = activeUser(1L);
        Category category = category(10L);
        CreateMapRequest request = audioYoutubeMapRequestWithUrl("https://example.com/watch?v=abc123");

        given(userRepository.findById(1L)).willReturn(Optional.of(creator));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));

        assertThatThrownBy(() -> mapService.createMap(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_youtube_url");

        verify(quizMapRepository, never()).save(any(QuizMap.class));
        verify(questionMediaRepository, never()).save(any(QuestionMedia.class));
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
    void createMapRejectsUserWithoutVerifiedEmail() {
        User creator = unverifiedUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(creator));

        assertThatThrownBy(() -> mapService.createMap(1L, audioYoutubeMapRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email_verification_required");

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
                .hasMessageContaining("duplicate_answer");

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
        return audioYoutubeMapRequest(thumbnailAssetId, "https://youtube.com/watch?v=---");
    }

    private ModifyMapRequest youtubeQuestionUpdateRequest(
            Long questionId,
            String sourceUrl,
            Long startTimeMs,
            Long endTimeMs
    ) {
        return new ModifyMapRequest(
                1,
                null,
                new ModifyMapRequest.QuestionsRequest(
                        null,
                        List.of(new ModifyMapRequest.UpdateQuestionRequest(
                                questionId,
                                "수정된 문제",
                                new ModifyMapRequest.MediaRequest(
                                        "YOUTUBE",
                                        null,
                                        sourceUrl,
                                        startTimeMs,
                                        endTimeMs
                                ),
                                List.of("정답")
                        )),
                        null
                )
        );
    }

    private CreateMapRequest audioYoutubeMapRequestWithUrl(String sourceUrl) {
        return audioYoutubeMapRequest(null, sourceUrl);
    }

    private CreateMapRequest audioYoutubeMapRequest(Long thumbnailAssetId, String sourceUrl) {
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
                                sourceUrl,
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
        user.verifyEmail("tester" + id + "@example.com", LocalDateTime.of(2026, 8, 1, 12, 0));
        return user;
    }

    private User unverifiedUser(Long id) {
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

    private QuestionMedia youtubeMedia(Question question) {
        return QuestionMedia.create(
                question,
                null,
                QuestionMediaSourceType.YOUTUBE,
                "https://youtube.com/watch?v=---",
                60_000,
                102_000,
                null
        );
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
