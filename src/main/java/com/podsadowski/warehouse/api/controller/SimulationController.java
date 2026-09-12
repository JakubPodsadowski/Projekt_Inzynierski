package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.OrderSurgeRequest;
import com.podsadowski.warehouse.api.dto.OrderSurgeResponse;
import com.podsadowski.warehouse.api.dto.SetSimulationSpeedRequest;
import com.podsadowski.warehouse.api.dto.SimulationStatusResponse;
import com.podsadowski.warehouse.api.service.SimulationControlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationControlService simulationControlService;

    @GetMapping("/status")
    public SimulationStatusResponse getStatus() {
        return simulationControlService.getStatus();
    }

    @PostMapping("/speed")
    public SimulationStatusResponse setSpeed(@Valid @RequestBody SetSimulationSpeedRequest request) {
        return simulationControlService.setSpeed(request);
    }

    @PostMapping("/pause")
    public SimulationStatusResponse pause() {
        return simulationControlService.pause();
    }

    @PostMapping("/resume")
    public SimulationStatusResponse resume() {
        return simulationControlService.resume();
    }

    @PostMapping("/chaos/order-surge")
    public OrderSurgeResponse orderSurge(@RequestBody(required = false) @Valid OrderSurgeRequest request) {
        return simulationControlService.orderSurge(request);
    }
}
