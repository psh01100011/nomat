package com.dogdog.nomat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "app.audio-processing.enabled=false",
        "app.asset.cleanup.enabled=false",
        "app.room.game-timer.initial-delay-ms=600000"
})
class FlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void emptyDatabaseIsMigratedAndValidatedAgainstJpaEntities() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");

        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                """, String.class);

        assertThat(tables).contains(
                "assets",
                "audio_processing_jobs",
                "game_sessions",
                "maps",
                "question_media",
                "users"
        );
    }
}
