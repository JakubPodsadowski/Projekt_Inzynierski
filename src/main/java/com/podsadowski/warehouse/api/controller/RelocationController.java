package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.BalanceResponse;
import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.ForceRelocationRequest;
import com.podsadowski.warehouse.api.dto.RelocationLogResponse;
import com.podsadowski.warehouse.api.service.RelocationWriteService;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/relocations")
@RequiredArgsConstructor
public class RelocationController {

    private final WorkforceReadService workforceReadService;
    private final RelocationWriteService relocationWriteService;

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RelocationLogResponse forceSchedule(@Valid @RequestBody ForceRelocationRequest request) {
        return relocationWriteService.forceSchedule(request);
    }

    @PostMapping("/{id}/cancel")
    public RelocationLogResponse cancelRelocation(@PathVariable Long id) {
        return relocationWriteService.cancel(id);
    }

    @PostMapping("/balance")
    public BalanceResponse balanceWorkforce() {
        return relocationWriteService.balance();
    }
}
