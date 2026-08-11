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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
