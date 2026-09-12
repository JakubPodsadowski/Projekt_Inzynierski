package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.OrderSurgeRequest;
import com.podsadowski.warehouse.api.dto.OrderSurgeResponse;
import com.podsadowski.warehouse.api.dto.SetSimulationSpeedRequest;
import com.podsadowski.warehouse.api.dto.SimulationStatusResponse;
import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.config.DataSourceModeHolder;
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
    private final DataSourceModeHolder dataSourceModeHolder;
    private final SimulationControl simulationControl;
    private final SimulationRuntimeState simulationRuntimeState;
    private final SimulationChaosService simulationChaosService;
    private final Clock clock;

    public SimulationStatusResponse getStatus() {
        boolean profileDemo = isDemoProfileActive();
        SystemMode mode = dataSourceModeHolder.get();
        boolean demoMode = mode == SystemMode.DEMO;
        boolean running = profileDemo && demoMode && simulationControl.isSimulationRunning();
        boolean paused = !running || simulationRuntimeState.isPaused();
        return new SimulationStatusResponse(
                LocalDateTime.now(clock),
                simulationRuntimeState.getSpeedMultiplier(),
                paused,
                running,
                profileDemo,
                mode
        );
    }

    public SimulationStatusResponse setSpeed(SetSimulationSpeedRequest request) {
        requireDemoMode();
        try {
            simulationRuntimeState.setSpeedMultiplier(request.speed());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage());
        }
        return getStatus();
    }

    public SimulationStatusResponse pause() {
        requireDemoMode();
        simulationRuntimeState.setPaused(true);
        simulationControl.stopSimulation();
        return getStatus();
    }

    public SimulationStatusResponse resume() {
        requireDemoMode();
        simulationRuntimeState.setPaused(false);
        simulationControl.startSimulation();
        return getStatus();
    }

    public OrderSurgeResponse orderSurge(OrderSurgeRequest request) {
        requireDemoMode();
        Integer items = request != null ? request.itemsPerEmployee() : null;
        var result = simulationChaosService.surgeOrders(items);
        return new OrderSurgeResponse(result.itemsCreated(), result.ordersCreated());
    }

    private void requireDemoMode() {
        if (!isDemoProfileActive()) {
            throw new InvalidRequestException("Simulation controls require spring profile 'demo'");
        }
        if (dataSourceModeHolder.get() != SystemMode.DEMO) {
            throw new InvalidRequestException("Simulation controls require DEMO mode");
        }
    }

    private boolean isDemoProfileActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains("demo");
    }
}
