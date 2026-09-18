package com.dogdog.nomat.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AudioExtractionFailureClassifierTest {

    private final AudioExtractionFailureClassifier classifier = new AudioExtractionFailureClassifier();

    @Test
    void classifiesUnrecognizedYtDlpFailureAsTemporaryDownloadError() {
        AudioProcessingFailureCode result = classifier.classify(
                "ERROR: remote server returned an unexpected response",
                "",
                true,
                false
        );

        assertThat(result).isEqualTo(AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR);
    }

    @Test
    void classifiesUnrecognizedFfmpegFailureAsTranscodingError() {
        AudioProcessingFailureCode result = classifier.classify(
                "",
                "Error while decoding stream",
                false,
                true
        );

        assertThat(result).isEqualTo(AudioProcessingFailureCode.TRANSCODING_ERROR);
    }

    @ParameterizedTest
    @MethodSource("sourceFailureLogs")
    void classifiesSourceFailureLogs(
            String ytDlpLog,
            String ffmpegLog,
            AudioProcessingFailureCode expectedCode
    ) {
        AudioProcessingFailureCode result = classifier.classify(ytDlpLog, ffmpegLog, true, true);

        assertThat(result).isEqualTo(expectedCode);
    }

    private static Stream<Arguments> sourceFailureLogs() {
        return Stream.of(
                Arguments.of(
                        "ERROR: Video unavailable. This video has been removed",
                        "",
                        AudioProcessingFailureCode.SOURCE_UNAVAILABLE
                ),
                Arguments.of(
                        "ERROR: Private video. Sign in if you've been granted access",
                        "",
                        AudioProcessingFailureCode.SOURCE_ACCESS_RESTRICTED
                ),
                Arguments.of(
                        "ERROR: This video is not available in your country",
                        "",
                        AudioProcessingFailureCode.SOURCE_ACCESS_RESTRICTED
                ),
                Arguments.of(
                        "ERROR: Requested format is not available",
                        "",
                        AudioProcessingFailureCode.AUDIO_STREAM_UNAVAILABLE
                ),
                Arguments.of(
                        "",
                        "Output file is empty, nothing was encoded",
                        AudioProcessingFailureCode.INVALID_AUDIO_RANGE
                )
        );
    }
}
