package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.map.entity.Category;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.Question;
import com.dogdog.nomat.domain.map.entity.QuestionAnswer;
import com.dogdog.nomat.domain.map.entity.QuestionStatus;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuestionAnswerRepository;
import com.dogdog.nomat.domain.map.repository.QuestionMediaRepository;
import com.dogdog.nomat.domain.map.repository.QuestionRepository;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.room.dto.CreateRoomRequest;
import com.dogdog.nomat.domain.room.dto.CreateRoomResponse;
import com.dogdog.nomat.domain.room.dto.CurrentRoomResponse;
import com.dogdog.nomat.domain.room.dto.JoinRoomRequest;
import com.dogdog.nomat.domain.room.dto.ModifyRoomSettingsRequest;
import com.dogdog.nomat.domain.room.dto.RoomDetailResponse;
import com.dogdog.nomat.domain.room.dto.RoomGameSnapshotResponse;
import com.dogdog.nomat.domain.room.dto.RoomListResponse;
import com.dogdog.nomat.domain.room.model.RoomDomainEventType;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
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
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuizMapRepository quizMapRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionAnswerRepository questionAnswerRepository;

    @Mock
    private QuestionMediaRepository questionMediaRepository;

    @Mock
    private RoomRedisRepository roomRedisRepository;

    @Spy
    private RoomStateMachine roomStateMachine = new RoomStateMachine();

    @Mock
    private RoomEventPublisher roomEventPublisher;

    @Mock
    private RoomGameProgressService roomGameProgressService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RoomService roomService;

    @Test
    void createRoomSavesWaitingRoomInRedis() {
        User host = user(3L);
        QuizMap map = publishedPublicMap(15L, 20);
        CreateRoomRequest request = request(15L, null, 10);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(roomRedisRepository.findJoinedRoomId(3L)).willReturn(Optional.empty());
        given(roomRedisRepository.nextRoomId()).willReturn(25L);
        given(roomRedisRepository.createRoom(org.mockito.ArgumentMatchers.any(RoomState.class))).willReturn(true);

        CreateRoomResponse response = roomService.createRoom(3L, request);

        assertThat(response.roomId()).isEqualTo(25L);
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.hostUserId()).isEqualTo(3L);

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRedisRepository).createRoom(roomCaptor.capture());
        RoomState room = roomCaptor.getValue();
        assertThat(room.roomId()).isEqualTo(25L);
        assertThat(room.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.mapId()).isEqualTo(15L);
        assertThat(room.categoryId()).isEqualTo(7L);
        assertThat(room.categoryName()).isEqualTo("음악");
        assertThat(room.mapQuestionCount()).isEqualTo(20);
        assertThat(room.hasPassword()).isFalse();
        assertThat(room.passwordHash()).isNull();
        assertThat(room.maxPlayers()).isEqualTo(10);
        assertThat(room.selectedQuestionCount()).isEqualTo(10);
        assertThat(room.answerTimeLimitSeconds()).isEqualTo(30);
        assertThat(room.timeLimitMode()).isEqualTo(TimeLimitMode.FIXED);
        assertThat(room.audioRepeatEnabled()).isTrue();
        assertThat(room.initialHintEnabled()).isTrue();
        assertThat(room.initialHintTriggerSeconds()).isEqualTo(10);
        assertThat(room.hostUserId()).isEqualTo(3L);
        assertThat(room.members()).hasSize(1);
        assertThat(room.members().getFirst().host()).isTrue();
    }

    @Test
    void createRoomStoresPasswordHashWhenPasswordExists() {
        User host = user(3L);
        QuizMap map = publishedPublicMap(15L, 20);
        CreateRoomRequest request = request(15L, "secret", 10);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(roomRedisRepository.findJoinedRoomId(3L)).willReturn(Optional.empty());
        given(roomRedisRepository.nextRoomId()).willReturn(25L);
        given(passwordEncoder.encode("secret")).willReturn("encoded-secret");
        given(roomRedisRepository.createRoom(org.mockito.ArgumentMatchers.any(RoomState.class))).willReturn(true);

        roomService.createRoom(3L, request);

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRedisRepository).createRoom(roomCaptor.capture());
        assertThat(roomCaptor.getValue().hasPassword()).isTrue();
        assertThat(roomCaptor.getValue().passwordHash()).isEqualTo("encoded-secret");
    }

    @Test
    void createRoomRejectsAlreadyJoinedUser() {
        User host = user(3L);
        QuizMap map = publishedPublicMap(15L, 20);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(roomRedisRepository.findJoinedRoomId(3L)).willReturn(Optional.of(99L));
        given(roomRedisRepository.findById(99L)).willReturn(Optional.of(room(99L, "참여 중인 방", 15L, 10)));

        assertThatThrownBy(() -> roomService.createRoom(3L, request(15L, null, 10)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getMessageCode()).isEqualTo("already_joined_room");
                    assertThat(exception.getData()).isEqualTo(new CurrentRoomResponse(99L, "WAITING"));
                });

        verify(roomRedisRepository, never()).nextRoomId();
    }

    @Test
    void createRoomRejectsMapThatIsNotPublicPublished() {
        User host = user(3L);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.createRoom(3L, request(15L, null, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("map_not_found");

        verify(roomRedisRepository, never()).findJoinedRoomId(3L);
    }

    @Test
    void createRoomRejectsTooManySelectedQuestions() {
        User host = user(3L);
        QuizMap map = publishedPublicMap(15L, 5);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));

        assertThatThrownBy(() -> roomService.createRoom(3L, request(15L, null, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(roomRedisRepository, never()).findJoinedRoomId(3L);
    }

    @Test
    void getRoomsReturnsJoinableWaitingRoomsByDefault() {
        RoomState joinableRoom = room(25L, "아이돌 노래 맞히기", 15L, 10);
        RoomState fullRoom = room(26L, "가득 찬 방", 15L, 1);
        RoomState playingRoom = room(27L, "게임 중인 방", 16L, 10).started("seed", now());
        given(roomRedisRepository.findRooms("latest"))
                .willReturn(List.of(playingRoom, fullRoom, joinableRoom));

        RoomListResponse response = roomService.getRooms(null, null, null, null, true, 0, 20, "latest");

        assertThat(response.rooms()).hasSize(1);
        assertThat(response.rooms().getFirst().roomId()).isEqualTo(25L);
        assertThat(response.rooms().getFirst().status()).isEqualTo("WAITING");
        assertThat(response.rooms().getFirst().questionType()).isEqualTo("AUDIO");
        assertThat(response.rooms().getFirst().categoryId()).isEqualTo(7L);
        assertThat(response.rooms().getFirst().categoryName()).isEqualTo("음악");
        assertThat(response.rooms().getFirst().thumbnailUrl()).isEqualTo("https://cdn.example.com/thumbnail.png");
        assertThat(response.rooms().getFirst().selectedQuestionCount()).isEqualTo(10);
        assertThat(response.rooms().getFirst().answerTimeLimitSeconds()).isEqualTo(30);
        assertThat(response.rooms().getFirst().timeLimitMode()).isEqualTo("FIXED");
        assertThat(response.rooms().getFirst().initialHintEnabled()).isTrue();
        assertThat(response.rooms().getFirst().initialHintTriggerSeconds()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void getRoomsFiltersByKeywordMapIdCategoryIdAndStatus() {
        RoomState targetRoom = room(25L, "아이돌 노래 맞히기", 15L, 10).started("seed", now());
        RoomState otherKeywordRoom = room(26L, "드라마 퀴즈", 15L, 10, 7L, "음악", "드라마 명장면 맞히기")
                .started("seed", now());
        RoomState otherMapRoom = room(27L, "아이돌 다른 맵", 99L, 10).started("seed", now());
        RoomState otherCategoryRoom = room(28L, "아이돌 카테고리 다름", 15L, 10, 9L, "상식")
                .started("seed", now());
        given(roomRedisRepository.findRooms("players"))
                .willReturn(List.of(targetRoom, otherKeywordRoom, otherMapRoom, otherCategoryRoom));

        RoomListResponse response = roomService.getRooms("아이돌", 15L, 7L, "PLAYING", false, 0, 20, "players");

        assertThat(response.rooms()).hasSize(1);
        assertThat(response.rooms().getFirst().roomId()).isEqualTo(25L);
        assertThat(response.rooms().getFirst().mapId()).isEqualTo(15L);
        assertThat(response.rooms().getFirst().status()).isEqualTo("PLAYING");
    }

    @Test
    void getRoomsFiltersByQuestionTypeAndPassword() {
        RoomState targetRoom = room(25L, "오디오 방", 15L, 10);
        RoomState passwordRoom = room(26L, "비밀번호 방", 15L, 10, true, "IMAGE");
        RoomState otherQuestionTypeRoom = room(27L, "이미지 방", 15L, 10, false, "IMAGE");
        given(roomRedisRepository.findRooms("latest"))
                .willReturn(List.of(targetRoom, passwordRoom, otherQuestionTypeRoom));

        RoomListResponse response = roomService.getRooms(
                null,
                null,
                null,
                "audio",
                false,
                true,
                null,
                false,
                0,
                20,
                "latest"
        );

        assertThat(response.rooms()).hasSize(1);
        assertThat(response.rooms().getFirst().roomId()).isEqualTo(25L);
    }

    @Test
    void getRoomsMatchesKeywordAgainstMapTitle() {
        RoomState targetRoom = room(25L, "같이 하실 분", 15L, 10);
        RoomState otherRoom = room(26L, "드라마 퀴즈", 15L, 10, 7L, "음악", "드라마 명장면 맞히기");
        given(roomRedisRepository.findRooms("latest"))
                .willReturn(List.of(targetRoom, otherRoom));

        RoomListResponse response = roomService.getRooms("아이돌", null, null, null, false, 0, 20, "latest");

        assertThat(response.rooms()).hasSize(1);
        assertThat(response.rooms().getFirst().roomId()).isEqualTo(25L);
    }

    @Test
    void getRoomsPaginatesFilteredRooms() {
        RoomState firstRoom = room(25L, "첫 번째 방", 15L, 10);
        RoomState secondRoom = room(26L, "두 번째 방", 15L, 10);
        RoomState thirdRoom = room(27L, "세 번째 방", 15L, 10);
        given(roomRedisRepository.findRooms("latest"))
                .willReturn(List.of(firstRoom, secondRoom, thirdRoom));

        RoomListResponse response = roomService.getRooms(null, null, null, null, false, 1, 2, "latest");

        assertThat(response.rooms()).hasSize(1);
        assertThat(response.rooms().getFirst().roomId()).isEqualTo(27L);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void getRoomsRejectsInvalidStatus() {
        assertThatThrownBy(() -> roomService.getRooms(null, null, null, "CLOSED", true, 0, 20, "latest"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void getCurrentRoomReturnsJoinedRoomStatus() {
        User user = user(3L);
        given(userRepository.findById(3L)).willReturn(Optional.of(user));
        given(roomRedisRepository.findJoinedRoomId(3L)).willReturn(Optional.of(25L));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room(25L, "참여 중인 방", 15L, 10)));

        CurrentRoomResponse response = roomService.getCurrentRoom(3L);

        assertThat(response).isEqualTo(new CurrentRoomResponse(25L, "WAITING"));
    }

    @Test
    void getCurrentRoomReturnsNullWhenUserHasNoRoom() {
        User user = user(3L);
        given(userRepository.findById(3L)).willReturn(Optional.of(user));
        given(roomRedisRepository.findJoinedRoomId(3L)).willReturn(Optional.empty());

        CurrentRoomResponse response = roomService.getCurrentRoom(3L);

        assertThat(response).isNull();
    }

    @Test
    void getGameSnapshotReturnsCurrentGameStateForRoomMember() {
        User user = user(4L);
        RoomState room = room(25L, "참여 중인 방", 15L, 10).withJoinedMember(member(4L)).started("seed", now());
        RoomGameQuestion question = new RoomGameQuestion(
                100L,
                1,
                "문제 지문",
                List.of("정답"),
                "정답",
                "https://cdn.example.com/audio.mp3",
                "YOUTUBE",
                1000,
                4000,
                3000
        );
        RoomGameState gameState = RoomGameState.started(
                        25L,
                        "seed",
                        List.of(question),
                        Map.of(3L, 100, 4L, 0),
                        now()
                )
                .withStartedQuestion(0, now(), 30)
                .withHintRevealed()
                .withSkipVote(4L);

        given(userRepository.findById(4L)).willReturn(Optional.of(user));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));

        RoomGameSnapshotResponse response = roomService.getGameSnapshot(4L, 25L);

        assertThat(response.roomId()).isEqualTo(25L);
        assertThat(response.status()).isEqualTo("PLAYING");
        assertThat(response.currentQuestion().questionId()).isEqualTo(100L);
        assertThat(response.currentQuestion().answerText()).isNull();
        assertThat(response.hintRevealed()).isTrue();
        assertThat(response.hintText()).isEqualTo("ㅈㄷ");
        assertThat(response.skipVoteCount()).isEqualTo(1);
        assertThat(response.skipVoteThreshold()).isEqualTo(2);
        assertThat(response.currentUserSkipVoted()).isTrue();
        assertThat(response.scores()).hasSize(2);
    }

    @Test
    void getRoomsRejectsInvalidPageSizeAndSort() {
        assertThatThrownBy(() -> roomService.getRooms(null, null, null, null, true, -1, 20, "latest"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        assertThatThrownBy(() -> roomService.getRooms(null, null, null, null, true, 0, 101, "latest"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        assertThatThrownBy(() -> roomService.getRooms(null, null, null, null, true, 0, 20, "unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void getRoomReturnsRoomDetail() {
        RoomState room = room(25L, "아이돌 노래 맞히기", 15L, 10);
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        RoomDetailResponse response = roomService.getRoom(25L);

        assertThat(response.roomId()).isEqualTo(25L);
        assertThat(response.title()).isEqualTo("아이돌 노래 맞히기");
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.hasPassword()).isFalse();
        assertThat(response.maxPlayers()).isEqualTo(10);
        assertThat(response.selectedQuestionCount()).isEqualTo(10);
        assertThat(response.answerTimeLimitSeconds()).isEqualTo(30);
        assertThat(response.timeLimitMode()).isEqualTo("FIXED");
        assertThat(response.audioRepeatEnabled()).isTrue();
        assertThat(response.initialHintEnabled()).isTrue();
        assertThat(response.initialHintTriggerSeconds()).isEqualTo(10);
        assertThat(response.map().mapId()).isEqualTo(15L);
        assertThat(response.map().title()).isEqualTo("20년대 아이돌 노래 맞히기");
        assertThat(response.map().questionType()).isEqualTo("AUDIO");
        assertThat(response.map().categoryId()).isEqualTo(7L);
        assertThat(response.map().categoryName()).isEqualTo("음악");
        assertThat(response.map().thumbnailUrl()).isEqualTo("https://cdn.example.com/thumbnail.png");
        assertThat(response.map().questionCount()).isEqualTo(20);
        assertThat(response.hostUserId()).isEqualTo(3L);
        assertThat(response.memberCount()).isEqualTo(1);
        assertThat(response.members()).hasSize(1);
        assertThat(response.members().getFirst().userId()).isEqualTo(3L);
        assertThat(response.members().getFirst().nickname()).isEqualTo("tester3");
        assertThat(response.members().getFirst().host()).isTrue();
        assertThat(response.createdAt()).isEqualTo(now());
    }

    @Test
    void getRoomReturnsPlayingRoomDetail() {
        RoomState room = room(25L, "아이돌 노래 맞히기", 15L, 10).started("seed", now());
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        RoomDetailResponse response = roomService.getRoom(25L);

        assertThat(response.status()).isEqualTo("PLAYING");
    }

    @Test
    void getRoomRejectsUnknownOrClosedRoom() {
        given(roomRedisRepository.findById(25L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> roomService.getRoom(25L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("room_not_found");

        given(roomRedisRepository.findById(26L)).willReturn(Optional.of(room(26L, "닫힌 방", 15L, 10).closed(now())));
        assertThatThrownBy(() -> roomService.getRoom(26L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("room_not_found");
    }

    @Test
    void joinRoomAddsMemberAndPublishesEvent() {
        User member = user(4L);
        RoomState room = room(25L, "아이돌 노래 맞히기", 15L, 10);
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findJoinedRoomId(4L)).willReturn(Optional.empty());
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.saveJoinedRoom(any(RoomState.class), eq(4L))).willReturn(true);

        RoomDetailResponse response = roomService.joinRoom(4L, 25L, new JoinRoomRequest(null));

        assertThat(response.roomId()).isEqualTo(25L);
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.members())
                .extracting(RoomDetailResponse.MemberResponse::userId)
                .containsExactly(3L, 4L);

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRedisRepository).saveJoinedRoom(roomCaptor.capture(), eq(4L));
        assertThat(roomCaptor.getValue().hasMember(4L)).isTrue();
        assertThat(roomCaptor.getValue().findMember(4L).orElseThrow().host()).isFalse();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.MEMBER_JOINED
                        && events.getFirst().roomId().equals(25L)
                        && events.getFirst().userId().equals(4L)
                        && events.getFirst().memberCount() == 2
        ));
    }

    @Test
    void joinRoomRejectsAlreadyJoinedUser() {
        User member = user(4L);
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findJoinedRoomId(4L)).willReturn(Optional.of(99L));
        given(roomRedisRepository.findById(99L)).willReturn(Optional.of(room(99L, "참여 중인 방", 15L, 10)));

        assertThatThrownBy(() -> roomService.joinRoom(4L, 25L, new JoinRoomRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getMessageCode()).isEqualTo("already_joined_room");
                    assertThat(exception.getData()).isEqualTo(new CurrentRoomResponse(99L, "WAITING"));
                });

        verify(roomRedisRepository, never()).findById(25L);
        verify(roomRedisRepository, never()).saveJoinedRoom(any(RoomState.class), eq(4L));
        verify(roomEventPublisher, never()).publish(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void joinRoomVerifiesPasswordRoomPassword() {
        User member = user(4L);
        RoomState room = passwordRoom(25L, "encoded-secret");
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findJoinedRoomId(4L)).willReturn(Optional.empty());
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(passwordEncoder.matches("secret", "encoded-secret")).willReturn(true);
        given(roomRedisRepository.saveJoinedRoom(any(RoomState.class), eq(4L))).willReturn(true);

        RoomDetailResponse response = roomService.joinRoom(4L, 25L, new JoinRoomRequest("secret"));

        assertThat(response.memberCount()).isEqualTo(2);
        verify(roomRedisRepository).saveJoinedRoom(any(RoomState.class), eq(4L));
        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void joinRoomRejectsInvalidPassword() {
        User member = user(4L);
        RoomState room = passwordRoom(25L, "encoded-secret");
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findJoinedRoomId(4L)).willReturn(Optional.empty());
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(passwordEncoder.matches("wrong", "encoded-secret")).willReturn(false);

        assertThatThrownBy(() -> roomService.joinRoom(4L, 25L, new JoinRoomRequest("wrong")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_room_password");

        verify(roomRedisRepository, never()).saveJoinedRoom(any(RoomState.class), eq(4L));
        verify(roomEventPublisher, never()).publish(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void joinRoomRejectsNotWaitingFullOrKickedRoom() {
        User member = user(4L);
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findJoinedRoomId(4L)).willReturn(Optional.empty());
        given(roomRedisRepository.findById(25L))
                .willReturn(Optional.of(room(25L, "게임 중인 방", 15L, 10).started("seed", now())));

        assertThatThrownBy(() -> roomService.joinRoom(4L, 25L, new JoinRoomRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot_join_room");

        given(roomRedisRepository.findById(26L)).willReturn(Optional.of(room(26L, "가득 찬 방", 15L, 1)));
        assertThatThrownBy(() -> roomService.joinRoom(4L, 26L, new JoinRoomRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot_join_room");

        RoomState kickedRoom = roomStateMachine.transition(
                room(27L, "강퇴된 방", 15L, 10)
                        .withJoinedMember(new com.dogdog.nomat.domain.room.model.RoomMember(
                                4L,
                                "tester4",
                                null,
                                false,
                                now()
                        )),
                com.dogdog.nomat.domain.room.model.RoomCommand.kick(3L, 4L, now())
        ).room();
        given(roomRedisRepository.findById(27L)).willReturn(Optional.of(kickedRoom));
        assertThatThrownBy(() -> roomService.joinRoom(4L, 27L, new JoinRoomRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("room_access_denied");

        verify(roomRedisRepository, never()).saveJoinedRoom(any(RoomState.class), eq(4L));
    }

    @Test
    void leaveRoomRemovesMemberAndPublishesMemberLeftEvent() {
        User member = user(4L);
        RoomState room = roomWithMember(25L, member(4L));
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        roomService.leaveRoom(4L, 25L);

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRedisRepository).saveLeftRoom(roomCaptor.capture(), eq(4L));
        RoomState savedRoom = roomCaptor.getValue();
        assertThat(savedRoom.hasMember(4L)).isFalse();
        assertThat(savedRoom.hostUserId()).isEqualTo(3L);
        assertThat(savedRoom.status()).isEqualTo(RoomStatus.WAITING);

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.MEMBER_LEFT
                        && events.getFirst().userId().equals(4L)
                        && events.getFirst().memberCount() == 1
        ));
    }

    @Test
    void leaveRoomTransfersHostWhenHostLeaves() {
        User host = user(3L);
        RoomState room = roomWithMember(25L, member(4L));
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        roomService.leaveRoom(3L, 25L);

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRedisRepository).saveLeftRoom(roomCaptor.capture(), eq(3L));
        RoomState savedRoom = roomCaptor.getValue();
        assertThat(savedRoom.hasMember(3L)).isFalse();
        assertThat(savedRoom.hostUserId()).isEqualTo(4L);
        assertThat(savedRoom.findMember(4L).orElseThrow().host()).isTrue();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 2
                        && events.get(0).type() == RoomDomainEventType.MEMBER_LEFT
                        && events.get(1).type() == RoomDomainEventType.HOST_CHANGED
                        && events.get(1).previousHostUserId().equals(3L)
                        && events.get(1).newHostUserId().equals(4L)
        ));
    }

    @Test
    void leaveRoomClosesRoomWhenLastMemberLeaves() {
        User host = user(3L);
        RoomState room = room(25L, "혼자 하는 방", 15L, 1);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        roomService.leaveRoom(3L, 25L);

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRedisRepository).saveLeftRoom(roomCaptor.capture(), eq(3L));
        assertThat(roomCaptor.getValue().status()).isEqualTo(RoomStatus.CLOSED);
        assertThat(roomCaptor.getValue().members()).isEmpty();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 2
                        && events.get(0).type() == RoomDomainEventType.MEMBER_LEFT
                        && events.get(1).type() == RoomDomainEventType.ROOM_CLOSED
        ));
    }

    @Test
    void modifyRoomSettingsUpdatesWaitingRoomAndPublishesEvent() {
        User host = user(3L);
        RoomState room = passwordRoom(25L, "encoded-password");
        ModifyRoomSettingsRequest request = new ModifyRoomSettingsRequest("", 8, 5, 45);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        RoomDetailResponse response = roomService.modifyRoomSettings(3L, 25L, request);

        assertThat(response.hasPassword()).isFalse();
        assertThat(response.maxPlayers()).isEqualTo(8);
        assertThat(response.selectedQuestionCount()).isEqualTo(5);
        assertThat(response.answerTimeLimitSeconds()).isEqualTo(45);

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRedisRepository).saveRoom(roomCaptor.capture());
        RoomState savedRoom = roomCaptor.getValue();
        assertThat(savedRoom.hasPassword()).isFalse();
        assertThat(savedRoom.passwordHash()).isNull();
        assertThat(savedRoom.maxPlayers()).isEqualTo(8);
        assertThat(savedRoom.selectedQuestionCount()).isEqualTo(5);
        assertThat(savedRoom.answerTimeLimitSeconds()).isEqualTo(45);

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.ROOM_SETTINGS_UPDATED
                        && events.getFirst().roomId().equals(25L)
                        && events.getFirst().hasPassword().equals(false)
                        && events.getFirst().maxPlayers() == 8
                        && events.getFirst().selectedQuestionCount() == 5
                        && events.getFirst().answerTimeLimitSeconds() == 45
        ));
    }

    @Test
    void modifyRoomSettingsRejectsNonHost() {
        User member = user(4L);
        RoomState room = roomWithMember(25L, member(4L));
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.modifyRoomSettings(
                4L,
                25L,
                new ModifyRoomSettingsRequest(null, 8, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_room_access");

        verify(roomRedisRepository, never()).saveRoom(any(RoomState.class));
    }

    @Test
    void leaveCurrentRoomByDisconnectLeavesJoinedRoomOnlyWhenRoomStillExists() {
        RoomState room = roomWithMember(25L, member(4L));
        given(roomRedisRepository.findJoinedRoomId(4L)).willReturn(Optional.of(25L));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        roomService.leaveCurrentRoomByDisconnect(4L);

        verify(roomRedisRepository).saveLeftRoom(any(RoomState.class), eq(4L));
        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void closeRoomDeletesWaitingRoomAndPublishesRoomClosedEvent() {
        User host = user(3L);
        RoomState room = roomWithMember(25L, member(4L));
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        roomService.closeRoom(3L, 25L);

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRedisRepository).deleteRoom(roomCaptor.capture());
        assertThat(roomCaptor.getValue().status()).isEqualTo(RoomStatus.CLOSED);

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.ROOM_CLOSED
        ));
    }

    @Test
    void closeRoomRejectsNonHostOrStartedRoom() {
        User member = user(4L);
        RoomState room = roomWithMember(25L, member(4L));
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.closeRoom(4L, 25L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_room_access");

        User host = user(3L);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(roomRedisRepository.findById(26L)).willReturn(Optional.of(room(26L, "시작된 방", 15L, 10)
                .started("seed", now())));

        assertThatThrownBy(() -> roomService.closeRoom(3L, 26L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("room_already_started");

        verify(roomRedisRepository, never()).deleteRoom(any(RoomState.class));
    }

    @Test
    void kickRoomMemberRemovesTargetAndPublishesMemberKickedEvent() {
        User host = user(3L);
        RoomState room = roomWithMember(25L, member(4L));
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        roomService.kickRoomMember(3L, 25L, 4L);

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRedisRepository).saveKickedRoom(roomCaptor.capture(), eq(4L));
        RoomState savedRoom = roomCaptor.getValue();
        assertThat(savedRoom.hasMember(4L)).isFalse();
        assertThat(savedRoom.isKicked(4L)).isTrue();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.MEMBER_KICKED
                        && events.getFirst().targetUserId().equals(4L)
                        && events.getFirst().memberCount() == 1
        ));
    }

    @Test
    void kickRoomMemberRejectsHostSelfKickNonHostAndStartedRoom() {
        User host = user(3L);
        RoomState room = roomWithMember(25L, member(4L));
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.kickRoomMember(3L, 25L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot_kick_room_member");

        User member = user(4L);
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findById(26L)).willReturn(Optional.of(roomWithMember(26L, member(4L))));
        assertThatThrownBy(() -> roomService.kickRoomMember(4L, 26L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_room_access");

        given(roomRedisRepository.findById(27L)).willReturn(Optional.of(roomWithMember(27L, member(4L))
                .started("seed", now())));
        assertThatThrownBy(() -> roomService.kickRoomMember(3L, 27L, 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot_kick_room_member");

        verify(roomRedisRepository, never()).saveKickedRoom(any(RoomState.class), eq(4L));
    }

    @Test
    void startGameAllowsSingleHostAndSavesGameState() {
        User host = user(3L);
        QuizMap map = publishedPublicMap(15L, 20);
        RoomState room = room(25L, "혼자 하는 방", 15L, 10);
        List<Question> questions = questions(map, 10);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(15L, QuestionStatus.ACTIVE))
                .willReturn(questions);
        given(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscIdAsc(
                org.mockito.ArgumentMatchers.anyCollection()
        )).willReturn(answers(questions));
        given(questionMediaRepository.findByQuestionIdIn(org.mockito.ArgumentMatchers.anyCollection()))
                .willReturn(List.of());

        RoomDetailResponse response = roomService.startGame(3L, 25L);

        assertThat(response.status()).isEqualTo("PLAYING");

        ArgumentCaptor<RoomState> roomCaptor = ArgumentCaptor.forClass(RoomState.class);
        ArgumentCaptor<RoomGameState> gameStateCaptor = ArgumentCaptor.forClass(RoomGameState.class);
        verify(roomRedisRepository).saveStartedRoom(roomCaptor.capture(), gameStateCaptor.capture());

        RoomState savedRoom = roomCaptor.getValue();
        RoomGameState gameState = gameStateCaptor.getValue();
        assertThat(savedRoom.status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(savedRoom.randomSeed()).isNotBlank();
        assertThat(gameState.roomId()).isEqualTo(25L);
        assertThat(gameState.randomSeed()).isEqualTo(savedRoom.randomSeed());
        assertThat(gameState.questions()).hasSize(10);
        assertThat(gameState.currentQuestionIndex()).isEqualTo(-1);
        assertThat(gameState.scores()).containsEntry(3L, 0);

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.GAME_STARTED
                        && events.getFirst().memberCount() == 1
        ));
        verify(roomGameProgressService).startFirstQuestion(savedRoom);
    }

    @Test
    void startGameRejectsNonHostStartedRoomMissingMapOrInsufficientQuestions() {
        User member = user(4L);
        RoomState room = roomWithMember(25L, member(4L));
        given(userRepository.findById(4L)).willReturn(Optional.of(member));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(publishedPublicMap(15L, 20)));
        assertThatThrownBy(() -> roomService.startGame(4L, 25L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_room_access");

        User host = user(3L);
        given(userRepository.findById(3L)).willReturn(Optional.of(host));
        given(roomRedisRepository.findById(26L)).willReturn(Optional.of(room(26L, "시작된 방", 15L, 10)
                .started("seed", now())));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(publishedPublicMap(15L, 20)));
        assertThatThrownBy(() -> roomService.startGame(3L, 26L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot_start_game");

        given(roomRedisRepository.findById(27L)).willReturn(Optional.of(room(27L, "삭제된 맵 방", 15L, 10)));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.empty());
        assertThatThrownBy(() -> roomService.startGame(3L, 27L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("map_not_found");

        QuizMap map = publishedPublicMap(15L, 20);
        given(roomRedisRepository.findById(28L)).willReturn(Optional.of(room(28L, "문제 부족 방", 15L, 10)));
        given(quizMapRepository.findByIdAndStatusAndVisibility(15L, MapStatus.PUBLISHED, MapVisibility.PUBLIC))
                .willReturn(Optional.of(map));
        given(questionRepository.findByMapIdAndStatusOrderByQuestionOrderAsc(15L, QuestionStatus.ACTIVE))
                .willReturn(questions(map, 3));
        assertThatThrownBy(() -> roomService.startGame(3L, 28L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot_start_game");

        verify(roomRedisRepository, never()).saveStartedRoom(any(RoomState.class), any(RoomGameState.class));
    }

    private CreateRoomRequest request(Long mapId, String password, int selectedQuestionCount) {
        return new CreateRoomRequest(
                mapId,
                "아이돌 노래 맞히기",
                password,
                10,
                selectedQuestionCount,
                30,
                "FIXED",
                true,
                true,
                10
        );
    }

    private User user(Long id) {
        User user = User.create("user" + id, "encoded-password", "tester" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private QuizMap publishedPublicMap(Long id, int questionCount) {
        QuizMap map = QuizMap.publish(
                user(1L),
                category(7L, "음악"),
                null,
                QuestionType.AUDIO,
                "20년대 아이돌 노래 맞히기",
                "설명",
                MapVisibility.PUBLIC,
                questionCount
        );
        ReflectionTestUtils.setField(map, "id", id);
        ReflectionTestUtils.setField(map, "version", 3);
        return map;
    }

    private List<Question> questions(QuizMap map, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> question(map, index))
                .toList();
    }

    private Question question(QuizMap map, int questionOrder) {
        Question question = Question.create(map, questionOrder, "문제 " + questionOrder);
        ReflectionTestUtils.setField(question, "id", (long) questionOrder);
        return question;
    }

    private List<QuestionAnswer> answers(List<Question> questions) {
        return questions.stream()
                .map(question -> answer(question, question.getId()))
                .toList();
    }

    private QuestionAnswer answer(Question question, Long id) {
        QuestionAnswer answer = QuestionAnswer.create(question, "정답 " + id, "정답" + id, true);
        ReflectionTestUtils.setField(answer, "id", id);
        return answer;
    }

    private RoomState room(Long roomId, String title, Long mapId, int maxPlayers) {
        return room(roomId, title, mapId, maxPlayers, 7L, "음악");
    }

    private RoomState room(
            Long roomId,
            String title,
            Long mapId,
            int maxPlayers,
            boolean hasPassword,
            String questionType
    ) {
        return room(
                roomId,
                title,
                mapId,
                maxPlayers,
                7L,
                "음악",
                "20년대 아이돌 노래 맞히기",
                hasPassword,
                questionType
        );
    }

    private RoomState roomWithMember(Long roomId, RoomMember member) {
        return room(roomId, "아이돌 노래 맞히기", 15L, 10).withJoinedMember(member);
    }

    private RoomMember member(Long userId) {
        return new RoomMember(userId, "tester" + userId, null, false, now().plusSeconds(userId));
    }

    private RoomState room(Long roomId, String title, Long mapId, int maxPlayers, Long categoryId, String categoryName) {
        return room(
                roomId,
                title,
                mapId,
                maxPlayers,
                categoryId,
                categoryName,
                "20년대 아이돌 노래 맞히기",
                false,
                "AUDIO"
        );
    }

    private RoomState room(
            Long roomId,
            String title,
            Long mapId,
            int maxPlayers,
            Long categoryId,
            String categoryName,
            String mapTitle
    ) {
        return room(roomId, title, mapId, maxPlayers, categoryId, categoryName, mapTitle, false, "AUDIO");
    }

    private RoomState room(
            Long roomId,
            String title,
            Long mapId,
            int maxPlayers,
            Long categoryId,
            String categoryName,
            String mapTitle,
            boolean hasPassword,
            String questionType
    ) {
        return RoomState.waiting(
                roomId,
                title,
                mapId,
                mapTitle,
                questionType,
                "https://cdn.example.com/thumbnail.png",
                categoryId,
                categoryName,
                20,
                3,
                hasPassword,
                hasPassword ? "encoded-password" : null,
                maxPlayers,
                10,
                30,
                TimeLimitMode.FIXED,
                true,
                true,
                10,
                new com.dogdog.nomat.domain.room.model.RoomMember(3L, "tester3", null, true, now()),
                now()
        );
    }

    private RoomState passwordRoom(Long roomId, String passwordHash) {
        return RoomState.waiting(
                roomId,
                "비밀번호 방",
                15L,
                "20년대 아이돌 노래 맞히기",
                null,
                7L,
                "음악",
                20,
                3,
                true,
                passwordHash,
                10,
                10,
                30,
                TimeLimitMode.FIXED,
                true,
                true,
                10,
                new com.dogdog.nomat.domain.room.model.RoomMember(3L, "tester3", null, true, now()),
                now()
        );
    }

    private Category category(Long id, String name) {
        Category category = Category.create(name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 8, 21, 20, 0);
    }
}
