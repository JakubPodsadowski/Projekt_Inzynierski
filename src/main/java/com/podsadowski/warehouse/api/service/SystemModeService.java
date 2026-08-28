package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.SetSystemModeRequest;
import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.api.dto.SystemModeResponse;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.simulation.SimulationControl;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class SystemModeService {

    private final Environment environment;
    private final SimulationControl simulationControl;

    public SystemModeResponse getMode() {
        boolean profileDemo = isDemoProfileActive();
        if (!profileDemo) {
            return new SystemModeResponse(SystemMode.REAL, false, false);
        }

        boolean running = simulationControl.isSimulationRunning();
        SystemMode mode = running ? SystemMode.DEMO : SystemMode.REAL;
        return new SystemModeResponse(mode, running, true);
    }

    public SystemModeResponse setMode(SetSystemModeRequest request) {
        if (!isDemoProfileActive()) {
            if (request.mode() == SystemMode.DEMO) {
                throw new InvalidRequestException("Demo mode requires spring profile 'demo'");
            }
            return getMode();
        }

        if (request.mode() == SystemMode.DEMO) {
            simulationControl.startSimulation();
        } else {
            simulationControl.stopSimulation();
        }

        return getMode();
    }

    private boolean isDemoProfileActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains("demo");
    }
}
