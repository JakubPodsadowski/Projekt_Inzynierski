package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.SetSystemModeRequest;
import com.podsadowski.warehouse.api.dto.SimulationStatusResponse;
import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.config.DataSourceModeHolder;
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

    private DataSourceModeHolder modeHolder;
    private SystemModeService systemModeService;

    @BeforeEach
    void setUp() {
        modeHolder = new DataSourceModeHolder(SystemMode.DEMO);
        systemModeService = new SystemModeService(modeHolder, simulationControlService);
    }

    @Test
    void getMode_withoutDemoProfile_returnsReal() {
        modeHolder.set(SystemMode.REAL);
        when(simulationControlService.getStatus()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.now(), 1, true, false, false, SystemMode.REAL
        ));

        var response = systemModeService.getMode();

        assertThat(response.mode()).isEqualTo(SystemMode.REAL);
        assertThat(response.profileDemo()).isFalse();
    }

    @Test
    void setMode_demoWithoutProfile_throws() {
        modeHolder.set(SystemMode.REAL);
        when(simulationControlService.getStatus()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.now(), 1, true, false, false, SystemMode.REAL
        ));

        assertThatThrownBy(() -> systemModeService.setMode(new SetSystemModeRequest(SystemMode.DEMO)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void setMode_demo_switchesDatasourceAndResumes() {
        modeHolder.set(SystemMode.REAL);
        when(simulationControlService.getStatus())
                .thenReturn(new SimulationStatusResponse(LocalDateTime.now(), 1, true, false, true, SystemMode.REAL))
                .thenReturn(new SimulationStatusResponse(LocalDateTime.now(), 1, false, true, true, SystemMode.DEMO));
        when(simulationControlService.resume()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.now(), 1, false, true, true, SystemMode.DEMO
        ));

        var response = systemModeService.setMode(new SetSystemModeRequest(SystemMode.DEMO));

        assertThat(modeHolder.get()).isEqualTo(SystemMode.DEMO);
        verify(simulationControlService).resume();
        assertThat(response.mode()).isEqualTo(SystemMode.DEMO);
        assertThat(response.profileDemo()).isTrue();
    }

    @Test
    void setMode_real_pausesThenSwitchesDatasource() {
        modeHolder.set(SystemMode.DEMO);
        when(simulationControlService.getStatus())
                .thenReturn(new SimulationStatusResponse(LocalDateTime.now(), 1, false, true, true, SystemMode.DEMO))
                .thenReturn(new SimulationStatusResponse(LocalDateTime.now(), 1, true, false, true, SystemMode.REAL));
        when(simulationControlService.pause()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.now(), 1, true, false, true, SystemMode.DEMO
        ));

        var response = systemModeService.setMode(new SetSystemModeRequest(SystemMode.REAL));

        verify(simulationControlService).pause();
        assertThat(modeHolder.get()).isEqualTo(SystemMode.REAL);
        assertThat(response.mode()).isEqualTo(SystemMode.REAL);
    }
}
