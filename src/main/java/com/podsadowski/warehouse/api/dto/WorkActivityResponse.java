package com.podsadowski.warehouse.api.dto;

import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.WorkActivityType;

import java.time.LocalDateTime;

public record WorkActivityResponse(
        Long id,
        Long employeeId,
        WorkActivityType type,
        DepartmentName departmentName,
        LocalDateTime occurredAt,
        int quantity,
        Long productId,
        String productSku,
        Long locationId,
        String locationCode,
        Long outboundOrderId,
        String orderNumber,
        Long inboundReceiptId,
        String receiptNumber,
        String message
) {
}
