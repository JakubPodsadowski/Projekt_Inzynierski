package com.podsadowski.warehouse.api.dto;

import com.podsadowski.warehouse.domain.entity.BreakTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEmployeeRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull Long departmentId,
        @NotNull BreakTier breakTier
) {
}
