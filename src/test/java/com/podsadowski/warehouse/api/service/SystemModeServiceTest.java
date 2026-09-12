package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.SetSystemModeRequest;
import com.podsadowski.warehouse.api.dto.SimulationStatusResponse;
import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemModeServiceTest {

    @Mock
    private SimulationControlService simulationControlService;

    private SystemModeService systemModeService;

    @BeforeEach
    void setUp() {
        systemModeService = new SystemModeService(simulationControlService);
    }

    @Test
    void getMode_withoutDemoProfile_returnsReal() {
        when(simulationControlService.getStatus()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.now(), 1, false, false, false
        ));

        var response = systemModeService.getMode();

        assertThat(response.mode()).isEqualTo(SystemMode.REAL);
        assertThat(response.profileDemo()).isFalse();
    }

    @Test
    void setMode_demoWithoutProfile_throws() {
        when(simulationControlService.getStatus()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.now(), 1, false, false, false
        ));

        assertThatThrownBy(() -> systemModeService.setMode(new SetSystemModeRequest(SystemMode.DEMO)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void setMode_demoProfile_resumesSimulation() {
        when(simulationControlService.getStatus())
                .thenReturn(new SimulationStatusResponse(LocalDateTime.now(), 1, true, false, true))
                .thenReturn(new SimulationStatusResponse(LocalDateTime.now(), 1, false, true, true));
        when(simulationControlService.resume()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.now(), 1, false, true, true
        ));

        var response = systemModeService.setMode(new SetSystemModeRequest(SystemMode.DEMO));

        verify(simulationControlService).resume();
        assertThat(response.mode()).isEqualTo(SystemMode.DEMO);
        assertThat(response.profileDemo()).isTrue();
    }

    @Test
    void setMode_real_pausesSimulation() {
        when(simulationControlService.getStatus())
                .thenReturn(new SimulationStatusResponse(LocalDateTime.now(), 1, false, true, true))
                .thenReturn(new SimulationStatusResponse(LocalDateTime.now(), 1, true, false, true));
        when(simulationControlService.pause()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.now(), 1, true, false, true
        ));

        var response = systemModeService.setMode(new SetSystemModeRequest(SystemMode.REAL));

        verify(simulationControlService).pause();
        assertThat(response.mode()).isEqualTo(SystemMode.REAL);
    }
}
