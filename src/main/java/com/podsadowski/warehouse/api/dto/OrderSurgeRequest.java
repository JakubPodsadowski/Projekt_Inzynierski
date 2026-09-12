package com.podsadowski.warehouse.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OrderSurgeRequest(
        @Min(1)
        @Max(2000)
        Integer itemsPerEmployee
) {
}
