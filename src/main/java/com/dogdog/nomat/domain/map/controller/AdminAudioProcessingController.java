package com.dogdog.nomat.domain.map.controller;

import com.dogdog.nomat.domain.map.dto.AdminAudioProcessingJobListResponse;
import com.dogdog.nomat.domain.map.dto.AdminAudioProcessingJobResponse;
import com.dogdog.nomat.domain.map.entity.AudioProcessingFailureType;
import com.dogdog.nomat.domain.map.entity.AudioProcessingJobStatus;
import com.dogdog.nomat.domain.map.service.AdminAudioProcessingService;
import com.dogdog.nomat.global.dto.ApiResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/audio-processing/jobs")
public class AdminAudioProcessingController {

    private final AdminAudioProcessingService adminAudioProcessingService;

    @GetMapping
    public ApiResponse<AdminAudioProcessingJobListResponse> getJobs(
            @RequestParam(required = false) AudioProcessingJobStatus status,
            @RequestParam(required = false) AudioProcessingFailureType failureType,
            @RequestParam(required = false) Long mapId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.of(
                "success_get_admin_audio_processing_jobs",
                adminAudioProcessingService.getJobs(
                        status,
                        failureType,
                        mapId,
                        createdFrom,
                        createdTo,
                        page,
                        size
                )
        );
    }

    @PostMapping("/{jobId}/retry")
    public ApiResponse<AdminAudioProcessingJobResponse> retryFailedJob(@PathVariable Long jobId) {
        return ApiResponse.of(
                "success_retry_admin_audio_processing_job",
                adminAudioProcessingService.retryFailedJob(jobId)
        );
    }

    @PostMapping("/{jobId}/recover-stale")
    public ApiResponse<AdminAudioProcessingJobResponse> recoverStaleJob(@PathVariable Long jobId) {
        return ApiResponse.of(
                "success_recover_stale_admin_audio_processing_job",
                adminAudioProcessingService.recoverStaleJob(jobId)
        );
    }
}
