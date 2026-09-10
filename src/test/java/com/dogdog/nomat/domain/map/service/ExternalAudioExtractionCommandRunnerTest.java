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
    void extractYoutubeSegmentUsesConfiguredMp3Bitrate() throws Exception {
        Path ytDlp = executableScript("yt-dlp", """
                #!/bin/sh
                printf 'audio'
                """);
        Path ffmpegArgs = tempDirectory.resolve("ffmpeg-args.txt");
        Path ffmpeg = executableScript("ffmpeg", """
                #!/bin/sh
                printf '%%s\\n' "$@" > "%s"
                for arg in "$@"; do
                  output="$arg"
                done
                printf 'mp3' > "$output"
                """.formatted(ffmpegArgs));

        AudioProcessingProperties properties = new AudioProcessingProperties();
        properties.setYtDlpPath(ytDlp.toString());
        properties.setFfmpegPath(ffmpeg.toString());
        properties.setOutputBitrate("128k");
        ExternalAudioExtractionCommandRunner runner = new ExternalAudioExtractionCommandRunner(properties);

        runner.extractYoutubeSegment(new YoutubeAudioExtractionCommand(
                "https://youtube.com/watch?v=test",
                1000,
                30000,
                tempDirectory.resolve("output.mp3")
        ));

        assertThat(Files.readString(ffmpegArgs))
                .contains("-acodec\nlibmp3lame")
                .contains("-b:a\n128k")
                .contains("-ar\n44100")
                .contains("-ac\n2");
        assertThat(Files.readString(tempDirectory.resolve("yt-dlp-args.txt"))).contains("--no-playlist");
    }

    @Test
    void extractYoutubeSegmentAllowsYtDlpBrokenPipeWhenFfmpegCreatedOutput() throws Exception {
        Path ytDlp = executableScript("yt-dlp", """
                #!/bin/sh
                yes '[download] progress' | head -n 200 >&2
                printf 'ERROR: unable to write data: [Errno 32] Broken pipe\\n' >&2
                exit 1
                """);
        Path ffmpeg = executableScript("ffmpeg", """
                #!/bin/sh
                for arg in "$@"; do
                  output="$arg"
                done
                printf 'mp3' > "$output"
                exit 0
                """);

        AudioProcessingProperties properties = new AudioProcessingProperties();
        properties.setYtDlpPath(ytDlp.toString());
        properties.setFfmpegPath(ffmpeg.toString());
        properties.setOutputBitrate("128k");
        ExternalAudioExtractionCommandRunner runner = new ExternalAudioExtractionCommandRunner(properties);
        Path outputFile = tempDirectory.resolve("output.mp3");

        runner.extractYoutubeSegment(new YoutubeAudioExtractionCommand(
                "https://youtube.com/watch?v=test",
                1000,
                7000,
                outputFile
        ));

        assertThat(outputFile).exists();
        assertThat(Files.size(outputFile)).isGreaterThan(0);
    }

    private Path executableScript(String name, String content) throws Exception {
        Path script = tempDirectory.resolve(name);
        if ("yt-dlp".equals(name)) {
            content = content.replace("#!/bin/sh\n", "#!/bin/sh\nprintf '%%s\\n' \"$@\" > \"%s\"\n"
                    .formatted(tempDirectory.resolve("yt-dlp-args.txt")));
        }
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
