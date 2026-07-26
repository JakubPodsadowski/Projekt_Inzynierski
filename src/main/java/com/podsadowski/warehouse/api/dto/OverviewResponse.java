package com.podsadowski.warehouse.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OverviewResponse(
        LocalDateTime timestamp,
        double deficitImbalance,
        List<DepartmentResponse> departments,
        long pendingRelocations,
        long inTransitRelocations,
        long employeesOnBreak,
        long totalEmployees
) {
}
