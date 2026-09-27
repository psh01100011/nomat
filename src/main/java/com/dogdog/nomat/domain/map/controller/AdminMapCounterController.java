package com.dogdog.nomat.domain.map.controller;

import com.dogdog.nomat.domain.map.dto.AdminMapCounterReconciliationResponse;
import com.dogdog.nomat.domain.map.service.AdminMapCounterService;
import com.dogdog.nomat.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/maps/{mapId}/counters")
public class AdminMapCounterController {

    private final AdminMapCounterService adminMapCounterService;

    @GetMapping
    public ApiResponse<AdminMapCounterReconciliationResponse> check(@PathVariable Long mapId) {
        return ApiResponse.of("success_check_map_counters", adminMapCounterService.check(mapId));
    }

    @PostMapping("/reconcile")
    public ApiResponse<AdminMapCounterReconciliationResponse> reconcile(@PathVariable Long mapId) {
        return ApiResponse.of("success_reconcile_map_counters", adminMapCounterService.reconcile(mapId));
    }
}
