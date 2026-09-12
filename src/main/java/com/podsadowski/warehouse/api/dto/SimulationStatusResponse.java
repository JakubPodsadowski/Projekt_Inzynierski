package com.podsadowski.warehouse.api.dto;

import java.time.LocalDateTime;

public record SimulationStatusResponse(
        LocalDateTime virtualTime,
        int speed,
        boolean paused,
        boolean running,
        boolean profileDemo
) {
}
