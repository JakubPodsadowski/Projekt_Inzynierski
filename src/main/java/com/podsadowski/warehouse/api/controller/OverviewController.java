package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.OverviewResponse;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final WorkforceReadService workforceReadService;

    @GetMapping
    public OverviewResponse getOverview() {
        return workforceReadService.getOverview();
    }
}
