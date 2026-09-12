package com.podsadowski.warehouse.api.dto;

public record OrderSurgeResponse(
        int itemsPerEmployee,
        int affectedEmployees
) {
}
