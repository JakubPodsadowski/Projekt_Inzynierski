package com.podsadowski.warehouse.api.dto;

public record InjectInboundResponse(
        Long receiptId,
        String receiptNumber,
        String externalRef,
        int totalItems
) {
}
