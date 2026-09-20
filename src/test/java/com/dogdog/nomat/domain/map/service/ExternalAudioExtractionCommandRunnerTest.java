package com.dogdog.nomat.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalAudioExtractionCommandRunnerTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void extractYoutubeSegmentDownloadsOnlyRequestedSectionBeforeTranscoding() throws Exception {
        Path ytDlpArgs = tempDirectory.resolve("yt-dlp-args.txt");
        Path downloadedSource = tempDirectory.resolve("source.webm");
        Path ytDlp = executableScript("yt-dlp", """
                #!/bin/sh
                printf '%%s\n' "$@" > "%s"
                printf 'source' > "%s"
                printf '%%s\n' "%s"
                """.formatted(ytDlpArgs, downloadedSource, downloadedSource));
        Path ffmpegArgs = tempDirectory.resolve("ffmpeg-args.txt");
        Path ffmpeg = executableScript("ffmpeg", """
                #!/bin/sh
                printf '%%s\n' "$@" > "%s"
                for arg in "$@"; do
                  output="$arg"
                done
                printf 'mp3' > "$output"
                """.formatted(ffmpegArgs));

        AudioProcessingProperties properties = new AudioProcessingProperties();
        properties.setYtDlpPath(ytDlp.toString());
        properties.setFfmpegPath(ffmpeg.toString());
        properties.setOutputBitrate("128k");
        ExternalAudioExtractionCommandRunner runner = new ExternalAudioExtractionCommandRunner(
                properties,
                new AudioExtractionFailureClassifier()
        );

        runner.extractYoutubeSegment(new YoutubeAudioExtractionCommand(
                "https://youtube.com/watch?v=test",
                3_601_250,
                90_000,
                tempDirectory.resolve("output.mp3")
        ));

        assertThat(Files.readString(ytDlpArgs))
                .contains("--no-playlist")
                .contains("--download-sections\n*3601.250-3691.250")
                .contains("--ffmpeg-location\n" + ffmpeg);
        assertThat(Files.readString(ffmpegArgs))
                .contains("-sseof\n-90.000")
                .contains("-i\n" + downloadedSource)
                .contains("-t\n90.000")
                .contains("-acodec\nlibmp3lame")
                .contains("-b:a\n128k");
    }

    private Path executableScript(String name, String content) throws Exception {
        Path script = tempDirectory.resolve(name);
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
