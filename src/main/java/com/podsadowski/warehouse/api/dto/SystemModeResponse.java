package com.podsadowski.warehouse.api.dto;

public record SystemModeResponse(
        SystemMode mode,
        boolean simulationRunning,
        boolean profileDemo
) {
}
