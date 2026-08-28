package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.SetSystemModeRequest;
import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.simulation.SimulationControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemModeServiceTest {

    @Mock
    private SimulationControl simulationControl;

    private MockEnvironment environment;
    private SystemModeService systemModeService;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        systemModeService = new SystemModeService(environment, simulationControl);
    }

    @Test
    void getMode_withoutDemoProfile_returnsReal() {
        var response = systemModeService.getMode();

        assertThat(response.mode()).isEqualTo(SystemMode.REAL);
        assertThat(response.profileDemo()).isFalse();
        assertThat(response.simulationRunning()).isFalse();
    }

    @Test
    void setMode_demoWithoutProfile_throws() {
        assertThatThrownBy(() -> systemModeService.setMode(new SetSystemModeRequest(SystemMode.DEMO)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void setMode_demoProfile_startsSimulation() {
        environment.setActiveProfiles("demo");
        when(simulationControl.isSimulationRunning()).thenReturn(true);

        var response = systemModeService.setMode(new SetSystemModeRequest(SystemMode.DEMO));

        verify(simulationControl).startSimulation();
        assertThat(response.mode()).isEqualTo(SystemMode.DEMO);
        assertThat(response.profileDemo()).isTrue();
    }

    @Test
    void setMode_realProfile_stopsSimulation() {
        environment.setActiveProfiles("demo");
        when(simulationControl.isSimulationRunning()).thenReturn(false);

        var response = systemModeService.setMode(new SetSystemModeRequest(SystemMode.REAL));

        verify(simulationControl).stopSimulation();
        assertThat(response.mode()).isEqualTo(SystemMode.REAL);
    }
}
