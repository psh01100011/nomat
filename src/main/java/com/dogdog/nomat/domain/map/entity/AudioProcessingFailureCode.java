package com.dogdog.nomat.domain.map.entity;

import lombok.Getter;

@Getter
public enum AudioProcessingFailureCode {
    SOURCE_UNAVAILABLE(
            AudioProcessingFailureType.SOURCE,
            false,
            false,
            "원본 영상을 사용할 수 없습니다. 영상 주소를 확인해주세요."
    ),
    SOURCE_ACCESS_RESTRICTED(
            AudioProcessingFailureType.SOURCE,
            false,
            false,
            "원본 영상에 접근할 수 없습니다. 공개 상태와 접근 제한을 확인해주세요."
    ),
    AUDIO_STREAM_UNAVAILABLE(
            AudioProcessingFailureType.SOURCE,
            false,
            false,
            "원본 영상에서 사용할 수 있는 오디오를 찾지 못했습니다."
    ),
    INVALID_AUDIO_RANGE(
            AudioProcessingFailureType.SOURCE,
            false,
            false,
            "설정한 오디오 구간을 처리할 수 없습니다. 시작과 종료 시간을 확인해주세요."
    ),
    DOWNLOAD_TEMPORARY_ERROR(
            AudioProcessingFailureType.SERVER,
            true,
            true,
            "원본 오디오를 가져오는 중 일시적인 문제가 발생했습니다."
    ),
    PROCESSING_TIMEOUT(
            AudioProcessingFailureType.SERVER,
            true,
            true,
            "오디오 처리 시간이 초과되었습니다."
    ),
    TRANSCODING_ERROR(
            AudioProcessingFailureType.SERVER,
            true,
            true,
            "오디오 변환 중 문제가 발생했습니다."
    ),
    STORAGE_ERROR(
            AudioProcessingFailureType.SERVER,
            true,
            true,
            "처리된 오디오를 저장하는 중 문제가 발생했습니다."
    ),
    INTERNAL_ERROR(
            AudioProcessingFailureType.SERVER,
            true,
            true,
            "오디오 처리 중 서버 문제가 발생했습니다."
    ),
    UNKNOWN(
            AudioProcessingFailureType.SERVER,
            true,
            true,
            "오디오 처리 중 서버 오류가 발생했습니다."
    );

    private final AudioProcessingFailureType failureType;
    private final boolean automaticRetryAllowed;
    private final boolean manualRetryAllowed;
    private final String userMessage;

    AudioProcessingFailureCode(
            AudioProcessingFailureType failureType,
            boolean automaticRetryAllowed,
            boolean manualRetryAllowed,
            String userMessage
    ) {
        this.failureType = failureType;
        this.automaticRetryAllowed = automaticRetryAllowed;
        this.manualRetryAllowed = manualRetryAllowed;
        this.userMessage = userMessage;
    }
}
