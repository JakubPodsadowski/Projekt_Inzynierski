package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.RelocationLogResponse;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/history")
    public List<RelocationLogResponse> getRelocationHistory(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Integer limit
    ) {
        return workforceReadService.findRelocationHistory(employeeId, limit);
    }

    @GetMapping("/history/{id}")
    public RelocationLogResponse getRelocationHistoryById(@PathVariable Long id) {
        return workforceReadService.findRelocationLogById(id);
    }
}
