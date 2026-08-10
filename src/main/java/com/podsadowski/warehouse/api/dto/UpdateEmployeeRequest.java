package com.podsadowski.warehouse.api.dto;

import com.podsadowski.warehouse.domain.entity.BreakTier;

public record UpdateEmployeeRequest(
        String firstName,
        String lastName,
        Long departmentId,
        BreakTier breakTier
) {
}
