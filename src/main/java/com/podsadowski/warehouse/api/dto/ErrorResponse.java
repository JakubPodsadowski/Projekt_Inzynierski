package com.podsadowski.warehouse.api.dto;

public record ErrorResponse(
        String error,
        String path
) {
}
