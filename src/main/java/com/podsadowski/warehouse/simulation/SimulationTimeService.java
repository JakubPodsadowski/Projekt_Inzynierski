package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SimulationTimeService {

    private final WarehouseProperties warehouseProperties;
    private final SimulationRuntimeState simulationRuntimeState;

    public long getSimulatedMinutesPerTick() {
        return warehouseProperties.getSimulation().getSimulatedMinutesPerTick();
    }

    /**
     * Simulated seconds to advance per scheduler tick at the current speed.
     * At 1x with a 1000 ms tick interval, advances 1 simulated second.
     */
    public long getSimulatedSecondsPerTick() {
        long tickIntervalMs = warehouseProperties.getScheduler().getTickIntervalMs();
        long baseSeconds = Math.max(1, tickIntervalMs / 1000);
        return baseSeconds * simulationRuntimeState.getSpeedMultiplier();
    }

    public long toRealSleepMs(long simulatedSeconds) {
        int speed = simulationRuntimeState.getSpeedMultiplier();
        if (speed <= 0) {
            return simulatedSeconds * 1000;
        }
        return Math.max(1, simulatedSeconds * 1000L / speed);
    }

    public long randomWorkIntervalSeconds() {
        WarehouseProperties.Simulation sim = warehouseProperties.getSimulation();
        long min = sim.getWorkIntervalSecondsMin();
        long max = sim.getWorkIntervalSecondsMax();
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}
