package com.podsadowski.warehouse.api.dto;

import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long employeeId,
        NotificationType type,
        String message,
        Long relocationLogId,
        DepartmentName fromDepartment,
        DepartmentName toDepartment,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        boolean read
) {
}
