package com.podsadowski.warehouse.dto;

import com.podsadowski.warehouse.entity.DepartmentName;

import java.time.LocalDateTime;

public record WorkProgressEvent(
        Long employeeId,
        DepartmentName departmentName,
        int itemsProcessed,
        LocalDateTime timestamp,
        long elapsedSimulatedSeconds
) {}
