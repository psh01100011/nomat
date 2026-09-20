package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AudioExtractionFailureClassifier {

    public AudioProcessingFailureCode classify(
            String ytDlpLog,
            String ffmpegLog,
            boolean ytDlpFailed,
            boolean ffmpegFailed
    ) {
        String ytDlpMessage = normalize(ytDlpLog);
        String ffmpegMessage = normalize(ffmpegLog);
        String combinedMessage = ytDlpMessage + "\n" + ffmpegMessage;

        if (containsAny(combinedMessage,
                "private video",
                "video is private",
                "sign in to confirm your age",
                "members-only",
                "members only",
                "not available in your country",
                "geo-restricted",
                "geo restricted")) {
            return AudioProcessingFailureCode.SOURCE_ACCESS_RESTRICTED;
        }

        if (containsAny(combinedMessage,
                "video unavailable",
                "video is unavailable",
                "video has been removed",
                "this video has been removed",
                "account associated with this video has been terminated")) {
            return AudioProcessingFailureCode.SOURCE_UNAVAILABLE;
        }

        if (containsAny(combinedMessage,
                "requested format is not available",
                "no video formats found",
                "does not have any formats",
                "does not contain any stream",
                "matches no streams")) {
            return AudioProcessingFailureCode.AUDIO_STREAM_UNAVAILABLE;
        }

        if (containsAny(ffmpegMessage,
                "output file is empty",
                "nothing was encoded",
                "could not seek to position",
                "invalid duration")) {
            return AudioProcessingFailureCode.INVALID_AUDIO_RANGE;
        }

        if (ffmpegFailed) {
            return AudioProcessingFailureCode.TRANSCODING_ERROR;
        }
        if (ytDlpFailed) {
            return AudioProcessingFailureCode.DOWNLOAD_TEMPORARY_ERROR;
        }
        return AudioProcessingFailureCode.UNKNOWN;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... patterns) {
        for (String pattern : patterns) {
            if (value.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
