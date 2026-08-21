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
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.room.dto.CreateRoomRequest;
import com.dogdog.nomat.domain.room.dto.CreateRoomResponse;
import com.dogdog.nomat.domain.room.dto.JoinRoomRequest;
import com.dogdog.nomat.domain.room.dto.RoomDetailResponse;
import com.dogdog.nomat.domain.room.dto.RoomListResponse;
import com.dogdog.nomat.domain.room.model.RoomDomainEventType;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
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
    private RoomRedisRepository roomRedisRepository;

    @Spy
    private RoomStateMachine roomStateMachine = new RoomStateMachine();

    @Mock
    private RoomEventPublisher roomEventPublisher;

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

        assertThatThrownBy(() -> roomService.createRoom(3L, request(15L, null, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already_joined_room");

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
        assertThat(response.map().categoryId()).isEqualTo(7L);
        assertThat(response.map().categoryName()).isEqualTo("음악");
        assertThat(response.map().thumbnailUrl()).isNull();
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

        assertThatThrownBy(() -> roomService.joinRoom(4L, 25L, new JoinRoomRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already_joined_room");

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

    private RoomState room(Long roomId, String title, Long mapId, int maxPlayers) {
        return room(roomId, title, mapId, maxPlayers, 7L, "음악");
    }

    private RoomState room(Long roomId, String title, Long mapId, int maxPlayers, Long categoryId, String categoryName) {
        return room(roomId, title, mapId, maxPlayers, categoryId, categoryName, "20년대 아이돌 노래 맞히기");
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
        return RoomState.waiting(
                roomId,
                title,
                mapId,
                mapTitle,
                null,
                categoryId,
                categoryName,
                20,
                3,
                false,
                null,
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
