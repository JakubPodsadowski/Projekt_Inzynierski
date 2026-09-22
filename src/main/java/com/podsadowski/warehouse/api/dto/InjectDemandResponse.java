package com.podsadowski.warehouse.api.dto;

public record InjectDemandResponse(
        Long orderId,
        String orderNumber,
        String externalRef,
        int totalItems
) {
}
