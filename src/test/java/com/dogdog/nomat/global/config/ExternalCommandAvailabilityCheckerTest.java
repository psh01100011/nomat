package com.dogdog.nomat.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExternalCommandAvailabilityCheckerTest {

    private final ExternalCommandAvailabilityChecker checker = new ExternalCommandAvailabilityChecker();

    @Test
    void acceptsExecutableVersionCommands() {
        String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        AudioProcessingProperties properties = new AudioProcessingProperties();
        properties.setYtDlpPath(javaCommand);
        properties.setFfmpegPath(javaCommand);

        assertThatCode(() -> checker.verifyAudioTools(properties)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingExecutable() {
        AudioProcessingProperties properties = new AudioProcessingProperties();
        properties.setYtDlpPath("/missing/yt-dlp");

        assertThatThrownBy(() -> checker.verifyAudioTools(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yt-dlp is not executable");
    }
}
