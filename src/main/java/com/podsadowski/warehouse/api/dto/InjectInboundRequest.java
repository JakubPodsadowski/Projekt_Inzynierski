package com.podsadowski.warehouse.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record InjectInboundRequest(
        String externalRef,
        @NotEmpty List<@Valid Line> lines
) {
    public record Line(
            @NotBlank String sku,
            @Positive int quantity,
            String preferredLocationCode
    ) {
    }
}
