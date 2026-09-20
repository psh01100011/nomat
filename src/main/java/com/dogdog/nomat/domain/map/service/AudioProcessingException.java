package com.dogdog.nomat.domain.map.service;

import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureCode;
import java.io.IOException;
import lombok.Getter;

@Getter
public class AudioProcessingException extends IOException {

    private final AudioProcessingFailureCode failureCode;

    public AudioProcessingException(AudioProcessingFailureCode failureCode, String technicalMessage) {
        super(technicalMessage);
        this.failureCode = failureCode;
    }

    public AudioProcessingException(
            AudioProcessingFailureCode failureCode,
            String technicalMessage,
            Throwable cause
    ) {
        super(technicalMessage, cause);
        this.failureCode = failureCode;
    }
}
