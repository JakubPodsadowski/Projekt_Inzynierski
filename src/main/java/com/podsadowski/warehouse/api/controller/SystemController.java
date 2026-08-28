package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.SetSystemModeRequest;
import com.podsadowski.warehouse.api.dto.SystemModeResponse;
import com.podsadowski.warehouse.api.service.SystemModeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemModeService systemModeService;

    @GetMapping("/mode")
    public SystemModeResponse getMode() {
        return systemModeService.getMode();
    }

    @PostMapping("/mode")
    public SystemModeResponse setMode(@Valid @RequestBody SetSystemModeRequest request) {
        return systemModeService.setMode(request);
    }
}
