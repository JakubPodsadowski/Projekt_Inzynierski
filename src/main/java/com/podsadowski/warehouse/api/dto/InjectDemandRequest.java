package com.podsadowski.warehouse.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public record InjectDemandRequest(
        String externalRef,
        boolean vip,
        LocalDateTime dueAt,
        @NotEmpty List<@Valid Line> lines
) {
    public record Line(
            @NotBlank String sku,
            @Positive int quantity
    ) {
    }
}
