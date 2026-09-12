package com.podsadowski.warehouse.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetSimulationSpeedRequest(
        @NotNull
        @Min(1)
        @Max(100)
        Integer speed
) {
}
