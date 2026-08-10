package com.podsadowski.warehouse.api.dto;

public record UpdateDepartmentRequest(
        Double requiredIph,
        Integer targetDailyItems,
        Double performanceMultiplier
) {
}
