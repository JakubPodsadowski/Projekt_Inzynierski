package com.podsadowski.warehouse.domain.dto;

import com.podsadowski.warehouse.domain.entity.DepartmentName;

import java.time.LocalDateTime;

public record WorkProgressEvent(
        Long employeeId,
        DepartmentName departmentName,
        int itemsProcessed,
        LocalDateTime timestamp,
        long elapsedSimulatedSeconds
) {}
