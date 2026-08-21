package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.MapVisibility;
import com.dogdog.nomat.domain.map.entity.QuestionType;
import com.dogdog.nomat.domain.map.entity.QuizMap;
import com.dogdog.nomat.domain.map.repository.QuizMapRepository;
import com.dogdog.nomat.domain.room.dto.CreateRoomRequest;
import com.dogdog.nomat.domain.room.dto.CreateRoomResponse;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
                null,
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
}
