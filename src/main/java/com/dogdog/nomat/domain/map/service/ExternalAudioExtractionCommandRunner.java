package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExternalAudioExtractionCommandRunner implements AudioExtractionCommandRunner {

    private static final int MAX_LOG_CHARS = 2000;

    private final AudioProcessingProperties properties;

    @Override
    public void extractYoutubeSegment(YoutubeAudioExtractionCommand command) throws IOException, InterruptedException {
        Files.createDirectories(command.outputFile().getParent());

        Path ytDlpLog = command.outputFile().resolveSibling("yt-dlp.log");
        Path ffmpegLog = command.outputFile().resolveSibling("ffmpeg.log");

        ProcessBuilder ytDlp = new ProcessBuilder(
                properties.getYtDlpPath(),
                "-f",
                "bestaudio",
                "-o",
                "-",
                command.sourceUrl()
        );
        ytDlp.redirectError(ytDlpLog.toFile());

        ProcessBuilder ffmpeg = new ProcessBuilder(
                properties.getFfmpegPath(),
                "-y",
                "-ss",
                seconds(command.startTimeMs()),
                "-i",
                "pipe:0",
                "-t",
                seconds(command.durationMs()),
                "-vn",
                "-acodec",
                "libmp3lame",
                "-b:a",
                properties.getOutputBitrate(),
                "-ar",
                "44100",
                "-ac",
                "2",
                command.outputFile().toString()
        );
        ffmpeg.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        ffmpeg.redirectError(ffmpegLog.toFile());

        List<Process> processes = ProcessBuilder.startPipeline(List.of(ytDlp, ffmpeg));
        waitForPipeline(processes, ytDlpLog, ffmpegLog);
    }

    private void waitForPipeline(List<Process> processes, Path ytDlpLog, Path ffmpegLog)
            throws InterruptedException, IOException {
        long timeoutSeconds = properties.getCommandTimeoutSeconds();
        for (Process process : processes) {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                processes.forEach(Process::destroyForcibly);
                throw new IOException("Audio extraction command timed out.");
            }
        }

        for (Process process : processes) {
            if (process.exitValue() != 0) {
                throw new IOException("Audio extraction command failed. yt-dlp="
                        + readLog(ytDlpLog)
                        + ", ffmpeg="
                        + readLog(ffmpegLog));
            }
        }
    }

    private String seconds(Integer millis) {
        return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
    }

    private String readLog(Path path) throws IOException {
        if (!Files.exists(path)) {
            return "";
        }

        String log = Files.readString(path, StandardCharsets.UTF_8);
        if (log.length() <= MAX_LOG_CHARS) {
            return log;
        }

        return log.substring(0, MAX_LOG_CHARS);
    }
}
