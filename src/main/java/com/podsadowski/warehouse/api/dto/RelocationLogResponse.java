package com.podsadowski.warehouse.api.dto;

import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.RelocationLogStatus;

import java.time.LocalDateTime;

public record RelocationLogResponse(
        Long id,
        Long employeeId,
        DepartmentName fromDepartment,
        DepartmentName toDepartment,
        RelocationLogStatus status,
        LocalDateTime scheduledAt,
        LocalDateTime transitStartedAt,
        LocalDateTime arrivedAt,
        double iphAtSource,
        double predictedIphAtTarget,
        Double iphOnArrival
) {
}
