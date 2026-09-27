package com.dogdog.nomat.global.config;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ExternalCommandAvailabilityChecker {

    private static final long VERSION_COMMAND_TIMEOUT_SECONDS = 10;

    public void verifyAudioTools(AudioProcessingProperties properties) {
        verify("yt-dlp", properties.getYtDlpPath(), "--version");
        verify("ffmpeg", properties.getFfmpegPath(), "-version");
    }

    private void verify(String toolName, String command, String versionArgument) {
        if (!StringUtils.hasText(command)) {
            throw new IllegalStateException(toolName + " path must be configured when audio processing is enabled");
        }

        Process process;
        try {
            process = new ProcessBuilder(command, versionArgument)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException exception) {
            throw unavailable(toolName, command, exception);
        }

        try {
            if (!process.waitFor(VERSION_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(toolName + " version check timed out: " + command);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(toolName + " version check failed: " + command);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException(toolName + " version check was interrupted", exception);
        }
    }

    private IllegalStateException unavailable(String toolName, String command, IOException cause) {
        return new IllegalStateException(toolName + " is not executable: " + command, cause);
    }
}
