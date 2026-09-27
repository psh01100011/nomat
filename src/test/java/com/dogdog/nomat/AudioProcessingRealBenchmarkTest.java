package com.dogdog.nomat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.domain.auth.token.AuthTokenProvider;
import com.dogdog.nomat.domain.map.dto.CreateMapRequest;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("audio-real-benchmark")
@ActiveProfiles("local")
@SpringBootTest(
        properties = {
                "app.audio-processing.enabled=false",
                "app.asset.cleanup.enabled=false",
                "app.room.game-timer.initial-delay-ms=3600000"
        }
)
class AudioProcessingRealBenchmarkTest {

    private static final int SOURCE_SAMPLE_COUNT = 23;
    private static final int QUESTION_COUNT = 100;
    private static final int MINIMUM_SUCCESS_COUNT = 100;
    private static final Duration COMPLETION_TIMEOUT = Duration.ofMinutes(20);
    private static final DateTimeFormatter TITLE_TIMESTAMP = DateTimeFormatter.ofPattern("MMdd-HHmmss");

    @Autowired
    private AuthTokenProvider authTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${audio.benchmark.server-base-url:http://127.0.0.1:8080/api}")
    private String serverBaseUrl;

    @Test
    void measuresCurrentYoutubeExtractionAndS3UploadPipeline() throws Exception {
        List<SourceSample> sourceSamples = findSourceSamples();
        assertThat(sourceSamples).hasSizeGreaterThanOrEqualTo(20);
        List<SourceSample> samples = repeatSamples(sourceSamples, QUESTION_COUNT);

        Long creatorId = requiredLong("""
                SELECT id
                FROM users
                WHERE status = 'ACTIVE'
                  AND email_verified_at IS NOT NULL
                ORDER BY id
                LIMIT 1
                """);
        Long categoryId = requiredLong("""
                SELECT id
                FROM categories
                WHERE active = TRUE
                ORDER BY id
                LIMIT 1
                """);

        LocalDateTime requestedAt = LocalDateTime.now();
        User creator = userRepository.findById(creatorId).orElseThrow();
        ApiCreationResult created = createMapThroughApi(
                benchmarkMapRequest(categoryId, samples, requestedAt),
                authTokenProvider.issueAccessToken(creator)
        );

        int maxProcessingCount = awaitTerminalJobs(created.mapId(), QUESTION_COUNT);
        BenchmarkResult result = loadResult(
                created.mapId(),
                requestedAt,
                samples,
                created.responseMillis(),
                maxProcessingCount
        );
        printResult(result);

        assertThat(result.successCount())
                .withFailMessage("At least %s successful real samples are required; failures=%s",
                        MINIMUM_SUCCESS_COUNT, result.failures())
                .isGreaterThanOrEqualTo(MINIMUM_SUCCESS_COUNT);
    }

    private List<SourceSample> findSourceSamples() {
        return jdbcTemplate.query("""
                        SELECT media.source_url, media.start_time_ms, media.end_time_ms
                        FROM audio_processing_jobs job
                        JOIN question_media media ON media.id = job.question_media_id
                        JOIN questions question ON question.id = media.question_id
                        JOIN maps map ON map.id = question.map_id
                        WHERE job.status = 'SUCCEEDED'
                          AND media.source_type = 'YOUTUBE'
                          AND media.source_url IS NOT NULL
                          AND media.start_time_ms IS NOT NULL
                          AND media.end_time_ms IS NOT NULL
                          AND map.title NOT LIKE '오디오 실측 %'
                          AND map.title NOT LIKE '오디오 API 실측 %'
                        ORDER BY job.completed_at DESC, job.id DESC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new SourceSample(
                        resultSet.getString("source_url"),
                        resultSet.getLong("start_time_ms"),
                        resultSet.getLong("end_time_ms")
                ),
                SOURCE_SAMPLE_COUNT
        );
    }

    private List<SourceSample> repeatSamples(List<SourceSample> sourceSamples, int count) {
        List<SourceSample> repeated = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            repeated.add(sourceSamples.get(index % sourceSamples.size()));
        }
        return repeated;
    }

    private ApiCreationResult createMapThroughApi(CreateMapRequest request, String accessToken) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/maps"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();

        long startedAt = System.nanoTime();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
        );
        long responseMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertThat(response.statusCode())
                .withFailMessage("Map creation API failed: status=%s body=%s", response.statusCode(), response.body())
                .isEqualTo(201);

        JsonNode responseBody = objectMapper.readTree(response.body());
        long mapId = responseBody.path("data").path("mapId").asLong();
        assertThat(mapId).isPositive();
        return new ApiCreationResult(mapId, responseMillis);
    }

    private CreateMapRequest benchmarkMapRequest(
            Long categoryId,
            List<SourceSample> samples,
            LocalDateTime requestedAt
    ) {
        List<CreateMapRequest.QuestionRequest> questions = new ArrayList<>(samples.size());
        for (int index = 0; index < samples.size(); index++) {
            SourceSample sample = samples.get(index);
            questions.add(new CreateMapRequest.QuestionRequest(
                    "오디오 실측 문제 " + (index + 1),
                    new CreateMapRequest.MediaRequest(
                            "YOUTUBE",
                            null,
                            sample.sourceUrl(),
                            sample.startTimeMs(),
                            sample.endTimeMs()
                    ),
                    List.of("benchmark-answer")
            ));
        }

        return new CreateMapRequest(
                "오디오 API 실측 " + requestedAt.format(TITLE_TIMESTAMP),
                categoryId,
                "AUDIO",
                null,
                "현재 서버의 YouTube 추출 및 S3 업로드 실측용 비공개 맵",
                "PRIVATE",
                questions
        );
    }

    private int awaitTerminalJobs(Long mapId, int expectedCount) throws Exception {
        long deadline = System.nanoTime() + COMPLETION_TIMEOUT.toNanos();
        int maxProcessingCount = 0;
        while (System.nanoTime() < deadline) {
            JobCounts counts = loadJobCounts(mapId);
            maxProcessingCount = Math.max(maxProcessingCount, counts.processingCount());
            if (counts.totalCount() == expectedCount && counts.terminalCount() == expectedCount) {
                return maxProcessingCount;
            }
            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        }
        throw new AssertionError("Real audio benchmark did not finish: " + loadJobCounts(mapId));
    }

    private JobCounts loadJobCounts(Long mapId) {
        return jdbcTemplate.queryForObject("""
                        SELECT
                          COUNT(*) AS total_count,
                          SUM(job.status IN ('SUCCEEDED', 'FAILED')) AS terminal_count,
                          SUM(job.status = 'PROCESSING') AS processing_count
                        FROM audio_processing_jobs job
                        JOIN question_media media ON media.id = job.question_media_id
                        JOIN questions question ON question.id = media.question_id
                        WHERE question.map_id = ?
                        """,
                (resultSet, rowNumber) -> new JobCounts(
                        resultSet.getInt("total_count"),
                        resultSet.getInt("terminal_count"),
                        resultSet.getInt("processing_count")
                ),
                mapId
        );
    }

    private BenchmarkResult loadResult(
            Long mapId,
            LocalDateTime requestedAt,
            List<SourceSample> samples,
            long apiResponseMillis,
            int maxProcessingCount
    ) {
        List<Long> processingMillis = jdbcTemplate.queryForList("""
                        SELECT TIMESTAMPDIFF(MICROSECOND, job.started_at, job.completed_at) / 1000
                        FROM audio_processing_jobs job
                        JOIN question_media media ON media.id = job.question_media_id
                        JOIN questions question ON question.id = media.question_id
                        WHERE question.map_id = ?
                          AND job.status = 'SUCCEEDED'
                        ORDER BY job.id
                        """,
                Long.class,
                mapId
        );
        List<Long> queueWaitMillis = jdbcTemplate.queryForList("""
                        SELECT TIMESTAMPDIFF(MICROSECOND, job.created_at, job.started_at) / 1000
                        FROM audio_processing_jobs job
                        JOIN question_media media ON media.id = job.question_media_id
                        JOIN questions question ON question.id = media.question_id
                        WHERE question.map_id = ?
                          AND job.status = 'SUCCEEDED'
                        ORDER BY job.id
                        """,
                Long.class,
                mapId
        );
        Map<String, Integer> failures = jdbcTemplate.query("""
                        SELECT COALESCE(job.failure_code, 'NONE') AS failure_code, COUNT(*) AS failure_count
                        FROM audio_processing_jobs job
                        JOIN question_media media ON media.id = job.question_media_id
                        JOIN questions question ON question.id = media.question_id
                        WHERE question.map_id = ?
                          AND job.status = 'FAILED'
                        GROUP BY job.failure_code
                        """,
                resultSet -> {
                    Map<String, Integer> counts = new java.util.LinkedHashMap<>();
                    while (resultSet.next()) {
                        counts.put(resultSet.getString("failure_code"), resultSet.getInt("failure_count"));
                    }
                    return counts;
                },
                mapId
        );
        LocalDateTime completedAt = jdbcTemplate.queryForObject("""
                        SELECT MAX(job.completed_at)
                        FROM audio_processing_jobs job
                        JOIN question_media media ON media.id = job.question_media_id
                        JOIN questions question ON question.id = media.question_id
                        WHERE question.map_id = ?
                        """,
                LocalDateTime.class,
                mapId
        );

        return new BenchmarkResult(
                mapId,
                samples.size(),
                (int) samples.stream().map(SourceSample::sourceUrl).distinct().count(),
                processingMillis.size(),
                samples.size() - processingMillis.size(),
                failures,
                apiResponseMillis,
                maxProcessingCount,
                Duration.between(requestedAt, completedAt).toMillis(),
                percentile(processingMillis, 50),
                percentile(processingMillis, 95),
                percentile(queueWaitMillis, 50),
                percentile(queueWaitMillis, 95)
        );
    }

    private long percentile(List<Long> values, int percentile) {
        assertThat(values).isNotEmpty();
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0));
    }

    private Long requiredLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        assertThat(value).isNotNull();
        return value;
    }

    private void printResult(BenchmarkResult result) {
        System.out.println("AUDIO_REAL_BENCHMARK mapId=" + result.mapId()
                + " samples=" + result.sampleCount()
                + " distinctVideos=" + result.distinctVideoCount()
                + " succeeded=" + result.successCount()
                + " failed=" + result.failureCount()
                + " apiResponseMs=" + result.apiResponseMillis()
                + " maxProcessing=" + result.maxProcessingCount()
                + " elapsedMs=" + result.elapsedMillis()
                + " processingP50Ms=" + result.processingP50Millis()
                + " processingP95Ms=" + result.processingP95Millis()
                + " queueWaitP50Ms=" + result.queueWaitP50Millis()
                + " queueWaitP95Ms=" + result.queueWaitP95Millis()
                + " failures=" + result.failures());
    }

    private record SourceSample(String sourceUrl, long startTimeMs, long endTimeMs) {
    }

    private record JobCounts(int totalCount, int terminalCount, int processingCount) {
    }

    private record ApiCreationResult(Long mapId, long responseMillis) {
    }

    private record BenchmarkResult(
            Long mapId,
            int sampleCount,
            int distinctVideoCount,
            int successCount,
            int failureCount,
            Map<String, Integer> failures,
            long apiResponseMillis,
            int maxProcessingCount,
            long elapsedMillis,
            long processingP50Millis,
            long processingP95Millis,
            long queueWaitP50Millis,
            long queueWaitP95Millis
    ) {
    }
}
