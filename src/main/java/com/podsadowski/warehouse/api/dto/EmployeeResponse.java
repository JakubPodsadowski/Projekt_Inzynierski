package com.podsadowski.warehouse.api.dto;

import com.podsadowski.warehouse.domain.entity.BreakTier;
import com.podsadowski.warehouse.domain.entity.DepartmentName;

import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String firstName,
        String lastName,
        Long currentDepartmentId,
        DepartmentName currentDepartment,
        double currentIph,
        BreakTier breakTier,
        boolean onBreak,
        RelocationStatus relocationStatus,
        Long targetDepartmentId,
        DepartmentName targetDepartment,
        LocalDateTime relocatingUntil,
        LocalDateTime lastRelocatedAt,
        int relocationCountToday,
        int orderItemsRemaining
) {
}
