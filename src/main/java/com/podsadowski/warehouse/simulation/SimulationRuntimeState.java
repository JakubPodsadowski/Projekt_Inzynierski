package com.podsadowski.warehouse.simulation;

import org.springframework.stereotype.Component;

/**
 * Runtime simulation controls (speed, pause) shared by worker threads and the tick scheduler.
 */
@Component
public class SimulationRuntimeState {

    public static final int MIN_SPEED = 1;
    public static final int MAX_SPEED = 100;

    private volatile int speedMultiplier = 1;
    private volatile boolean paused = false;

    public int getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(int speedMultiplier) {
        if (speedMultiplier < MIN_SPEED || speedMultiplier > MAX_SPEED) {
            throw new IllegalArgumentException(
                    "speed must be between " + MIN_SPEED + " and " + MAX_SPEED);
        }
        this.speedMultiplier = speedMultiplier;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
