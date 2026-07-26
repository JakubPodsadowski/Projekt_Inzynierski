package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/relocations")
@RequiredArgsConstructor
public class RelocationController {

    private final WorkforceReadService workforceReadService;

    @GetMapping("/pending")
    public List<EmployeeResponse> getPendingRelocations() {
        return workforceReadService.findPendingRelocations();
    }

    @GetMapping("/in-transit")
    public List<EmployeeResponse> getInTransitRelocations() {
        return workforceReadService.findInTransitRelocations();
    }
}
