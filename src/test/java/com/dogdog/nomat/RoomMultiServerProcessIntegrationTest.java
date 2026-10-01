package com.dogdog.nomat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag("multi-server-process")
@Testcontainers(disabledWithoutDocker = true)
class RoomMultiServerProcessIntegrationTest {

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379);

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void roomEventsAndGameProgressSurviveServerFailure() throws Exception {
        Path processDirectory = Files.createTempDirectory("nomat-room-multi-server-");
        int firstPort = availablePort();
        int secondPort = availablePort();

        try (ServerProcess firstServer = startServer(1, firstPort, processDirectory)) {
            awaitServerReady(firstServer);
            long mapId = seedPublishedTextMap();
            try (ServerProcess secondServer = startServer(2, secondPort, processDirectory)) {
                awaitServerReady(secondServer);

                AuthSession host = loginGuest(firstServer.baseUrl(), "검증방장");
                AuthSession guest = loginGuest(secondServer.baseUrl(), "검증참가자");
                long roomId = createRoom(firstServer.baseUrl(), host.accessToken(), mapId);

                try (StompTestClient hostSocket = connect(firstServer, host.accessToken(), roomId)) {
                    joinRoom(secondServer.baseUrl(), guest.accessToken(), roomId);
                    assertThat(hostSocket.awaitEvent("MEMBER_JOINED", Duration.ofSeconds(5)).path("userId").asLong())
                            .isEqualTo(guest.userId());

                    try (StompTestClient guestSocket = connect(secondServer, guest.accessToken(), roomId)) {
                        hostSocket.sendChat(roomId, host.accessToken(), "server-one-chat");
                        assertChat(hostSocket, "server-one-chat");
                        assertChat(guestSocket, "server-one-chat");

                        guestSocket.sendChat(roomId, guest.accessToken(), "server-two-chat");
                        assertChat(hostSocket, "server-two-chat");
                        assertChat(guestSocket, "server-two-chat");

                        startGame(firstServer.baseUrl(), host.accessToken(), roomId);
                        hostSocket.awaitEvent("GAME_STARTED", Duration.ofSeconds(5));
                        guestSocket.awaitEvent("GAME_STARTED", Duration.ofSeconds(5));
                        hostSocket.awaitEvent("QUESTION_STARTED", Duration.ofSeconds(5));
                        guestSocket.awaitEvent("QUESTION_STARTED", Duration.ofSeconds(5));

                        firstServer.crash();

                        try (StompTestClient reconnectedHost = connect(secondServer, host.accessToken(), roomId)) {
                            JsonNode snapshot = getSnapshot(secondServer.baseUrl(), host.accessToken(), roomId);
                            assertThat(snapshot.path("status").asText()).isEqualTo("PLAYING");
                            assertThat(snapshot.path("currentQuestion").path("questionNumber").asInt()).isEqualTo(1);

                            reconnectedHost.sendChat(roomId, host.accessToken(), "after-failover");
                            assertChat(reconnectedHost, "after-failover");
                            assertChat(guestSocket, "after-failover");

                            reconnectedHost.awaitEvent("HINT_REVEALED", Duration.ofSeconds(12));
                            guestSocket.awaitEvent("HINT_REVEALED", Duration.ofSeconds(12));
                            reconnectedHost.awaitEvent("QUESTION_ENDED", Duration.ofSeconds(12));
                            guestSocket.awaitEvent("QUESTION_ENDED", Duration.ofSeconds(12));
                            reconnectedHost.awaitEvent("GAME_ENDED", Duration.ofSeconds(12));
                            guestSocket.awaitEvent("GAME_ENDED", Duration.ofSeconds(12));
                        }
                    }
                }

                assertThat(gameSessionCount(roomId)).isOne();
            }
        }
    }

    private ServerProcess startServer(int sequence, int port, Path directory) throws Exception {
        String classpath = System.getProperty("multi-server-test.classpath");
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalStateException("Multi-server test runtime classpath was not configured.");
        }

        Path logFile = directory.resolve("server-" + sequence + ".log");
        ProcessBuilder processBuilder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                classpath,
                NomatApplication.class.getName(),
                "--spring.profiles.active=local",
                "--server.port=" + port,
                "--spring.datasource.url=" + MYSQL.getJdbcUrl(),
                "--spring.datasource.username=" + MYSQL.getUsername(),
                "--spring.datasource.password=" + MYSQL.getPassword(),
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=true",
                "--spring.data.redis.url=redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379),
                "--app.audio-processing.enabled=false",
                "--app.asset.cleanup.enabled=false",
                "--app.email-verification.delivery-mode=log",
                "--app.asset.s3.bucket=multi-server-test",
                "--app.asset.s3.public-base-url=https://cdn.example.com",
                "--app.room.game-timer.initial-delay-ms=100",
                "--app.room.game-timer.fixed-delay-ms=50",
                "--app.room.game-timer.lease-duration-ms=1000",
                "--app.room.game-timer.execution-lock-duration-ms=3000",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN"
        );
        processBuilder.environment().put("AWS_ACCESS_KEY_ID", "test");
        processBuilder.environment().put("AWS_SECRET_ACCESS_KEY", "test");
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(logFile.toFile());
        return new ServerProcess(processBuilder.start(), port, logFile);
    }

    private void awaitServerReady(ServerProcess server) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            if (!server.process().isAlive()) {
                throw new AssertionError("Server exited before ready:\n" + server.log());
            }
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(
                        HttpRequest.newBuilder(URI.create(server.baseUrl() + "/rooms?size=1"))
                                .timeout(Duration.ofSeconds(2))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // The process can accept connections only after Spring finishes initialization.
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Server did not become ready:\n" + server.log());
    }

    private AuthSession loginGuest(String baseUrl, String nickname) throws Exception {
        JsonNode data = postJson(baseUrl, "/auth/guest", Map.of("nickname", nickname), null).path("data");
        return new AuthSession(data.path("userId").asLong(), data.path("accessToken").asText());
    }

    private long createRoom(String baseUrl, String accessToken, long mapId) throws Exception {
        Map<String, Object> request = Map.ofEntries(
                Map.entry("mapId", mapId),
                Map.entry("title", "다중 서버 검증방"),
                Map.entry("maxPlayers", 2),
                Map.entry("selectedQuestionCount", 1),
                Map.entry("answerTimeLimitSeconds", 8),
                Map.entry("timeLimitMode", "FIXED"),
                Map.entry("audioRepeatEnabled", false),
                Map.entry("initialHintEnabled", true),
                Map.entry("initialHintTriggerSeconds", 2)
        );
        return postJson(baseUrl, "/rooms", request, accessToken).path("data").path("roomId").asLong();
    }

    private void joinRoom(String baseUrl, String accessToken, long roomId) throws Exception {
        postJson(baseUrl, "/rooms/" + roomId + "/join", Map.of(), accessToken);
    }

    private void startGame(String baseUrl, String accessToken, long roomId) throws Exception {
        postJson(baseUrl, "/rooms/" + roomId + "/start", Map.of(), accessToken);
    }

    private JsonNode getSnapshot(String baseUrl, String accessToken, long roomId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/rooms/" + roomId + "/game/snapshot"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertSuccess(response);
        return OBJECT_MAPPER.readTree(response.body()).path("data");
    }

    private JsonNode postJson(String baseUrl, String path, Object body, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        HttpResponse<String> response = HTTP_CLIENT.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertSuccess(response);
        return OBJECT_MAPPER.readTree(response.body());
    }

    private void assertSuccess(HttpResponse<String> response) {
        assertThat(response.statusCode())
                .withFailMessage("Unexpected HTTP response %s: %s", response.statusCode(), response.body())
                .isBetween(200, 299);
    }

    private StompTestClient connect(ServerProcess server, String accessToken, long roomId) throws Exception {
        StompTestClient client = new StompTestClient(server.websocketUrl(), accessToken);
        try {
            client.connect();
            client.subscribe(roomId);
            return client;
        } catch (Exception exception) {
            client.close();
            throw new AssertionError("WebSocket connection failed. Server log:\n" + server.log(), exception);
        }
    }

    private void assertChat(StompTestClient client, String content) throws Exception {
        assertThat(client.awaitEvent("CHAT_MESSAGE", Duration.ofSeconds(5)).path("content").asText())
                .isEqualTo(content);
    }

    private long seedPublishedTextMap() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        )) {
            long userId = insertAndReturnId(connection, """
                    INSERT INTO users (
                        login_id, password_hash, nickname, status, created_at, updated_at
                    ) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """, "multi_server_creator", "unused", "검증제작자");
            long categoryId = insertAndReturnId(
                    connection,
                    "INSERT INTO categories (name, active) VALUES (?, true)",
                    "검증 카테고리"
            );
            long mapId = insertAndReturnId(connection, """
                    INSERT INTO maps (
                        creator_id, category_id, question_type, title, description, status, visibility,
                        version, question_count, play_count, like_count, favorite_count, comment_count,
                        created_at, updated_at, published_at
                    ) VALUES (?, ?, 'TEXT', ?, ?, 'PUBLISHED', 'PUBLIC', 0, 1, 0, 0, 0, 0,
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """, userId, categoryId, "다중 서버 검증 맵", "통합 테스트용 맵");
            long questionId = insertAndReturnId(connection, """
                    INSERT INTO questions (
                        map_id, question_order, prompt_text, status, created_at, updated_at
                    ) VALUES (?, 1, ?, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """, mapId, "다중 서버에서도 게임이 계속될까요?");
            insertAndReturnId(connection, """
                    INSERT INTO question_answers (
                        question_id, answer_text, answer_key, is_primary, created_at
                    ) VALUES (?, ?, ?, true, CURRENT_TIMESTAMP(6))
                    """, questionId, "네", "네");
            return mapId;
        }
    }

    private long insertAndReturnId(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            assertThat(statement.executeUpdate()).isOne();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private long gameSessionCount(long roomId) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        ); PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM game_sessions WHERE source_room_id = ?"
        )) {
            statement.setLong(1, roomId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private int availablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record AuthSession(long userId, String accessToken) {
    }

    private static final class ServerProcess implements AutoCloseable {

        private final Process process;
        private final int port;
        private final Path logFile;

        private ServerProcess(Process process, int port, Path logFile) {
            this.process = process;
            this.port = port;
            this.logFile = logFile;
        }

        private Process process() {
            return process;
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + port + "/api";
        }

        private URI websocketUrl() {
            return URI.create("ws://127.0.0.1:" + port + "/api/ws");
        }

        private String log() throws Exception {
            return Files.exists(logFile) ? Files.readString(logFile) : "";
        }

        private void crash() throws Exception {
            if (!process.isAlive()) {
                return;
            }
            process.destroyForcibly();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        }

        @Override
        public void close() throws Exception {
            if (!process.isAlive()) {
                return;
            }
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static final class StompTestClient implements WebSocket.Listener, AutoCloseable {

        private final URI uri;
        private final String accessToken;
        private final CountDownLatch connected = new CountDownLatch(1);
        private final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        private final StringBuilder frames = new StringBuilder();
        private volatile WebSocket webSocket;

        private StompTestClient(URI uri, String accessToken) {
            this.uri = uri;
            this.accessToken = accessToken;
        }

        private void connect() throws Exception {
            webSocket = HTTP_CLIENT.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(uri, this)
                    .get(10, TimeUnit.SECONDS);
            sendFrame("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\nAuthorization:Bearer "
                    + accessToken + "\n\n");
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();
        }

        private void subscribe(long roomId) throws Exception {
            sendFrame("SUBSCRIBE\nid:" + UUID.randomUUID() + "\ndestination:/topic/rooms/" + roomId
                    + "\nack:auto\n\n");
            Thread.sleep(150);
        }

        private void sendChat(long roomId, String token, String content) throws Exception {
            String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "content", content,
                    "clientMessageId", UUID.randomUUID().toString()
            ));
            sendFrame("SEND\ndestination:/app/rooms/" + roomId + "/messages\ncontent-type:application/json"
                    + "\nAuthorization:Bearer " + token
                    + "\ncontent-length:" + body.getBytes(StandardCharsets.UTF_8).length + "\n\n" + body);
        }

        private JsonNode awaitEvent(String type, Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                long remaining = deadline - System.nanoTime();
                JsonNode event = events.poll(Math.max(1, remaining), TimeUnit.NANOSECONDS);
                if (event == null) {
                    break;
                }
                if (type.equals(event.path("type").asText())) {
                    return event;
                }
            }
            throw new AssertionError("Timed out waiting for room event " + type);
        }

        private void sendFrame(String frame) {
            webSocket.sendText(frame + '\0', true).join();
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (frames) {
                frames.append(data);
                int terminator;
                while ((terminator = frames.indexOf("\0")) >= 0) {
                    handleFrame(frames.substring(0, terminator));
                    frames.delete(0, terminator + 1);
                }
            }
            webSocket.request(1);
            return null;
        }

        private void handleFrame(String frame) {
            if (frame.startsWith("CONNECTED")) {
                connected.countDown();
                return;
            }
            if (!frame.startsWith("MESSAGE")) {
                return;
            }
            int bodyStart = frame.indexOf("\n\n");
            if (bodyStart < 0) {
                bodyStart = frame.indexOf("\r\n\r\n");
                bodyStart = bodyStart < 0 ? -1 : bodyStart + 2;
            }
            if (bodyStart < 0) {
                return;
            }
            try {
                events.add(OBJECT_MAPPER.readTree(frame.substring(bodyStart + 2)));
            } catch (Exception ignored) {
                // Protocol errors are surfaced by the timeout with the missing expected event.
            }
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected.countDown();
        }

        @Override
        public void close() {
            WebSocket socket = webSocket;
            if (socket != null && !socket.isOutputClosed()) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "test-complete");
            }
        }
    }
}
