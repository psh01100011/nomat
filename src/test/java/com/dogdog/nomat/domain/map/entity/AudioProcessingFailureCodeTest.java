package com.dogdog.nomat.domain.map.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class AudioProcessingFailureCodeTest {

    @Test
    void sourceFailuresDoNotAllowAutomaticOrManualRetry() {
        EnumSet<AudioProcessingFailureCode> sourceFailures = EnumSet.of(
                AudioProcessingFailureCode.SOURCE_UNAVAILABLE,
                AudioProcessingFailureCode.SOURCE_ACCESS_RESTRICTED,
                AudioProcessingFailureCode.AUDIO_STREAM_UNAVAILABLE,
                AudioProcessingFailureCode.INVALID_AUDIO_RANGE
        );

        assertThat(sourceFailures).allSatisfy(code -> {
            assertThat(code.getFailureType()).isEqualTo(AudioProcessingFailureType.SOURCE);
            assertThat(code.isAutomaticRetryAllowed()).isFalse();
            assertThat(code.isManualRetryAllowed()).isFalse();
            assertThat(code.getUserMessage()).isNotBlank();
        });
    }

    @Test
    void knownServerFailuresAllowAutomaticAndManualRetry() {
        EnumSet<AudioProcessingFailureCode> serverFailures = EnumSet.of(
                AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR,
                AudioProcessingFailureCode.PROCESSING_TIMEOUT,
                AudioProcessingFailureCode.TRANSCODING_ERROR,
                AudioProcessingFailureCode.STORAGE_ERROR,
                AudioProcessingFailureCode.INTERNAL_ERROR
        );

        assertThat(serverFailures).allSatisfy(code -> {
            assertThat(code.getFailureType()).isEqualTo(AudioProcessingFailureType.SERVER);
            assertThat(code.isAutomaticRetryAllowed()).isTrue();
            assertThat(code.isManualRetryAllowed()).isTrue();
            assertThat(code.getUserMessage()).isNotBlank();
        });
    }

    @Test
    void unknownFailureAllowsAutomaticAndManualRetry() {
        AudioProcessingFailureCode code = AudioProcessingFailureCode.UNKNOWN;

        assertThat(code.getFailureType()).isEqualTo(AudioProcessingFailureType.SERVER);
        assertThat(code.isAutomaticRetryAllowed()).isTrue();
        assertThat(code.isManualRetryAllowed()).isTrue();
    }
}
