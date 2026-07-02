package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SimulationTimeService {

    private final WarehouseProperties warehouseProperties;

    public long getSimulatedMinutesPerTick() {
        return warehouseProperties.getSimulation().getSimulatedMinutesPerTick();
    }

    public long getAccelerationFactor() {
        WarehouseProperties.Simulation sim = warehouseProperties.getSimulation();
        return sim.getSimulatedMinutesPerTick() * 60 * 1000 / sim.getTickIntervalMs();
    }

    public long toRealSleepMs(long simulatedSeconds) {
        long factor = getAccelerationFactor();
        if (factor <= 0) {
            return simulatedSeconds * 1000;
        }
        return Math.max(1, simulatedSeconds * 1000 / factor);
    }

    public long randomWorkIntervalSeconds() {
        WarehouseProperties.Simulation sim = warehouseProperties.getSimulation();
        long min = sim.getWorkIntervalSecondsMin();
        long max = sim.getWorkIntervalSecondsMax();
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}
