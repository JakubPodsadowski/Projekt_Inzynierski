package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.OrderSurgeRequest;
import com.podsadowski.warehouse.api.dto.OrderSurgeResponse;
import com.podsadowski.warehouse.api.dto.SetSimulationSpeedRequest;
import com.podsadowski.warehouse.api.dto.SimulationStatusResponse;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.simulation.SimulationChaosService;
import com.podsadowski.warehouse.simulation.SimulationControl;
import com.podsadowski.warehouse.simulation.SimulationRuntimeState;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class SimulationControlService {

    private final Environment environment;
    private final SimulationControl simulationControl;
    private final SimulationRuntimeState simulationRuntimeState;
    private final SimulationChaosService simulationChaosService;
    private final Clock clock;

    public SimulationStatusResponse getStatus() {
        boolean profileDemo = isDemoProfileActive();
        return new SimulationStatusResponse(
                LocalDateTime.now(clock),
                simulationRuntimeState.getSpeedMultiplier(),
                simulationRuntimeState.isPaused(),
                profileDemo && simulationControl.isSimulationRunning(),
                profileDemo
        );
    }

    public SimulationStatusResponse setSpeed(SetSimulationSpeedRequest request) {
        requireDemoProfile();
        try {
            simulationRuntimeState.setSpeedMultiplier(request.speed());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage());
        }
        return getStatus();
    }

    public SimulationStatusResponse pause() {
        requireDemoProfile();
        simulationRuntimeState.setPaused(true);
        simulationControl.stopSimulation();
        return getStatus();
    }

    public SimulationStatusResponse resume() {
        requireDemoProfile();
        simulationRuntimeState.setPaused(false);
        simulationControl.startSimulation();
        return getStatus();
    }

    public OrderSurgeResponse orderSurge(OrderSurgeRequest request) {
        requireDemoProfile();
        Integer items = request != null ? request.itemsPerEmployee() : null;
        int affected = simulationChaosService.surgeOrders(items);
        int applied = items != null ? items : SimulationChaosService.DEFAULT_SURGE_ITEMS;
        return new OrderSurgeResponse(applied, affected);
    }

    private void requireDemoProfile() {
        if (!isDemoProfileActive()) {
            throw new InvalidRequestException("Simulation controls require spring profile 'demo'");
        }
    }

    private boolean isDemoProfileActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains("demo");
    }
}
