package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.SetSystemModeRequest;
import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.api.dto.SystemModeResponse;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.config.DataSourceModeHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Switches between REAL ({@code warehouse_db}) and DEMO ({@code warehouse_demo}) datasources
 * and starts/stops the demo simulation accordingly.
 */
@Service
@RequiredArgsConstructor
public class SystemModeService {

    private final DataSourceModeHolder dataSourceModeHolder;
    private final SimulationControlService simulationControlService;

    public SystemModeResponse getMode() {
        var status = simulationControlService.getStatus();
        return new SystemModeResponse(status.mode(), status.running(), status.profileDemo());
    }

    public synchronized SystemModeResponse setMode(SetSystemModeRequest request) {
        var status = simulationControlService.getStatus();
        if (!status.profileDemo()) {
            if (request.mode() == SystemMode.DEMO) {
                throw new InvalidRequestException("Demo mode requires spring profile 'demo'");
            }
            return getMode();
        }

        SystemMode target = request.mode();
        if (target == dataSourceModeHolder.get()) {
            if (target == SystemMode.DEMO && !status.running()) {
                simulationControlService.resume();
            }
            return getMode();
        }

        if (target == SystemMode.REAL) {
            // Stop workers while still routed to the demo database.
            simulationControlService.pause();
            dataSourceModeHolder.set(SystemMode.REAL);
        } else {
            dataSourceModeHolder.set(SystemMode.DEMO);
            simulationControlService.resume();
        }

        return getMode();
    }
}
