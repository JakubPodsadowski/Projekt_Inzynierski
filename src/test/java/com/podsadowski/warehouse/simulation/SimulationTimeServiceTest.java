package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationTimeServiceTest {

    private SimulationRuntimeState runtimeState;
    private SimulationTimeService timeService;

    @BeforeEach
    void setUp() {
        runtimeState = new SimulationRuntimeState();
        WarehouseProperties properties = new WarehouseProperties();
        properties.getScheduler().setTickIntervalMs(1000);
        properties.getSimulation().setWorkIntervalSecondsMin(45);
        properties.getSimulation().setWorkIntervalSecondsMax(45);
        timeService = new SimulationTimeService(properties, runtimeState);
    }

    @Test
    void toRealSleepMs_at1x_matchesSimulatedSeconds() {
        runtimeState.setSpeedMultiplier(1);
        assertThat(timeService.toRealSleepMs(10)).isEqualTo(10_000);
    }

    @Test
    void toRealSleepMs_at5x_isFiveTimesFaster() {
        runtimeState.setSpeedMultiplier(5);
        assertThat(timeService.toRealSleepMs(10)).isEqualTo(2_000);
    }

    @Test
    void getSimulatedSecondsPerTick_scalesWithSpeed() {
        runtimeState.setSpeedMultiplier(1);
        assertThat(timeService.getSimulatedSecondsPerTick()).isEqualTo(1);

        runtimeState.setSpeedMultiplier(5);
        assertThat(timeService.getSimulatedSecondsPerTick()).isEqualTo(5);
    }
}
