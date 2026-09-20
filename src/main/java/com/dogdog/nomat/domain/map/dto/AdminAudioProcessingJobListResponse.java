package com.dogdog.nomat.domain.map.dto;

import com.dogdog.nomat.domain.map.entity.AudioProcessingJob;
import java.util.List;
import org.springframework.data.domain.Page;

public record AdminAudioProcessingJobListResponse(
        List<AdminAudioProcessingJobResponse> jobs,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static AdminAudioProcessingJobListResponse from(Page<AudioProcessingJob> jobs) {
        return new AdminAudioProcessingJobListResponse(
                jobs.getContent().stream()
                        .map(AdminAudioProcessingJobResponse::from)
                        .toList(),
                jobs.getNumber(),
                jobs.getSize(),
                jobs.getTotalElements(),
                jobs.getTotalPages(),
                jobs.hasNext()
        );
    }
}
