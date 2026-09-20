package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private final AudioExtractionFailureClassifier failureClassifier;

    @Override
    public void extractYoutubeSegment(YoutubeAudioExtractionCommand command) throws IOException, InterruptedException {
        try {
            Files.createDirectories(command.outputFile().getParent());
        } catch (IOException exception) {
            throw new AudioProcessingException(
                    AudioProcessingFailureCode.INTERNAL_ERROR,
                    "Failed to prepare audio processing directory.",
                    exception
            );
        }

        Path ytDlpLog = command.outputFile().resolveSibling("yt-dlp.log");
        Path ytDlpOutput = command.outputFile().resolveSibling("yt-dlp-output.txt");
        Path ffmpegLog = command.outputFile().resolveSibling("ffmpeg.log");
        Path sourceTemplate = command.outputFile().resolveSibling("source.%(ext)s");

        List<String> ytDlpCommand = new ArrayList<>(List.of(
                properties.getYtDlpPath(),
                "--no-playlist",
                "--force-overwrites",
                "-f",
                "bestaudio",
                "--download-sections",
                downloadSection(command)
        ));
        addFfmpegLocation(ytDlpCommand);
        ytDlpCommand.addAll(List.of(
                "--print",
                "after_move:filepath",
                "-o",
                sourceTemplate.toString(),
                command.sourceUrl()
        ));
        ProcessBuilder ytDlp = new ProcessBuilder(ytDlpCommand);
        ytDlp.redirectOutput(ytDlpOutput.toFile());
        ytDlp.redirectError(ytDlpLog.toFile());
        runYtDlp(ytDlp, ytDlpLog);

        Path downloadedSource = downloadedSource(ytDlpOutput, command.outputFile().getParent());
        ProcessBuilder ffmpeg = new ProcessBuilder(
                properties.getFfmpegPath(),
                "-y",
                "-sseof",
                "-" + seconds(command.durationMs()),
                "-i",
                downloadedSource.toString(),
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
        runFfmpeg(ffmpeg, ytDlpLog, ffmpegLog, command.outputFile());
    }

    private void runYtDlp(ProcessBuilder processBuilder, Path ytDlpLog) throws IOException, InterruptedException {
        Process process = start(processBuilder);
        waitFor(process);
        if (process.exitValue() == 0) {
            return;
        }

        String ytDlpFullLog = readFullLog(ytDlpLog);
        AudioProcessingFailureCode failureCode = failureClassifier.classify(ytDlpFullLog, "", true, false);
        throw commandFailure(failureCode, ytDlpFullLog, "");
    }

    private void runFfmpeg(
            ProcessBuilder processBuilder,
            Path ytDlpLog,
            Path ffmpegLog,
            Path outputFile
    ) throws IOException, InterruptedException {
        Process process = start(processBuilder);
        waitFor(process);

        String ytDlpFullLog = readFullLog(ytDlpLog);
        String ffmpegFullLog = readFullLog(ffmpegLog);
        boolean ffmpegFailed = process.exitValue() != 0;
        boolean outputMissing = !Files.exists(outputFile) || Files.size(outputFile) == 0;
        if (!ffmpegFailed && !outputMissing) {
            return;
        }

        AudioProcessingFailureCode failureCode = failureClassifier.classify(
                ytDlpFullLog,
                ffmpegFullLog,
                false,
                ffmpegFailed || outputMissing
        );
        throw commandFailure(failureCode, ytDlpFullLog, ffmpegFullLog);
    }

    private Process start(ProcessBuilder processBuilder) throws IOException {
        try {
            return processBuilder.start();
        } catch (IOException exception) {
            throw new AudioProcessingException(
                    AudioProcessingFailureCode.INTERNAL_ERROR,
                    "Failed to start audio extraction command.",
                    exception
            );
        }
    }

    private void waitFor(Process process) throws IOException, InterruptedException {
        if (process.waitFor(properties.getCommandTimeoutSeconds(), TimeUnit.SECONDS)) {
            return;
        }

        process.destroyForcibly();
        throw new AudioProcessingException(
                AudioProcessingFailureCode.PROCESSING_TIMEOUT,
                "Audio extraction command timed out."
        );
    }

    private Path downloadedSource(Path ytDlpOutput, Path workingDirectory) throws IOException {
        Path normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
        return Files.readAllLines(ytDlpOutput, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((first, second) -> second)
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> path.getParent().equals(normalizedWorkingDirectory))
                .filter(Files::isRegularFile)
                .orElseThrow(() -> new AudioProcessingException(
                        AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR,
                        "Downloaded audio section file was not found."
                ));
    }

    private String downloadSection(YoutubeAudioExtractionCommand command) {
        long endTimeMs = (long) command.startTimeMs() + command.durationMs();
        return "*" + seconds(command.startTimeMs()) + "-" + seconds(endTimeMs);
    }

    private void addFfmpegLocation(List<String> command) {
        Path configuredPath = Path.of(properties.getFfmpegPath());
        if (!configuredPath.isAbsolute() && configuredPath.getParent() == null) {
            return;
        }
        command.add("--ffmpeg-location");
        command.add(properties.getFfmpegPath());
    }

    private String seconds(long millis) {
        return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
    }

    private String readFullLog(Path path) throws IOException {
        if (!Files.exists(path)) {
            return "";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private AudioProcessingException commandFailure(
            AudioProcessingFailureCode failureCode,
            String ytDlpLog,
            String ffmpegLog
    ) {
        return new AudioProcessingException(
                failureCode,
                "Audio extraction command failed. yt-dlp="
                        + truncateLog(ytDlpLog)
                        + ", ffmpeg="
                        + truncateLog(ffmpegLog)
        );
    }

    private String truncateLog(String log) {
        if (log.length() <= MAX_LOG_CHARS) {
            return log;
        }
        return log.substring(0, MAX_LOG_CHARS);
    }
}
