package com.podsadowski.warehouse.simulation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class NoOpSimulationControl implements SimulationControl {

    @Override
    public void startSimulation() {
        // no simulation outside demo profile
    }

    @Override
    public void stopSimulation() {
        // no simulation outside demo profile
    }

    @Override
    public boolean isSimulationRunning() {
        return false;
    }
}
