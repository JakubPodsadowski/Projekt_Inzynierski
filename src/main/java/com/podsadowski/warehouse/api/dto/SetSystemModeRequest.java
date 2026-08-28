package com.podsadowski.warehouse.api.dto;

import jakarta.validation.constraints.NotNull;

public record SetSystemModeRequest(
        @NotNull SystemMode mode
) {
}
