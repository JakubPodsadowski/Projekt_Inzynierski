package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.SetSystemModeRequest;
import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.api.dto.SystemModeResponse;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Legacy DEMO/REAL toggle — delegates to {@link SimulationControlService}.
 */
@Service
@RequiredArgsConstructor
public class SystemModeService {

    private final SimulationControlService simulationControlService;

    public SystemModeResponse getMode() {
        var status = simulationControlService.getStatus();
        SystemMode mode = status.running() && !status.paused() ? SystemMode.DEMO : SystemMode.REAL;
        return new SystemModeResponse(mode, status.running(), status.profileDemo());
    }

    public SystemModeResponse setMode(SetSystemModeRequest request) {
        if (!simulationControlService.getStatus().profileDemo()) {
            if (request.mode() == SystemMode.DEMO) {
                throw new InvalidRequestException("Demo mode requires spring profile 'demo'");
            }
            return getMode();
        }

        if (request.mode() == SystemMode.DEMO) {
            simulationControlService.resume();
        } else {
            simulationControlService.pause();
        }
        return getMode();
    }
}
