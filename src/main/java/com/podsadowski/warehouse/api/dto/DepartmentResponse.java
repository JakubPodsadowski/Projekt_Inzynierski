package com.podsadowski.warehouse.api.dto;

import com.podsadowski.warehouse.domain.entity.DepartmentName;

public record DepartmentResponse(
        Long id,
        DepartmentName name,
        double currentIph,
        double requiredIph,
        double performanceMultiplier,
        double deficit,
        int targetDailyItems,
        long employeeCount
) {
}
