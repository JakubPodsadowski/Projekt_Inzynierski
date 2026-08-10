package com.podsadowski.warehouse.api.dto;

import jakarta.validation.constraints.NotNull;

public record ForceRelocationRequest(
        @NotNull Long employeeId,
        @NotNull Long toDepartmentId
) {
}
