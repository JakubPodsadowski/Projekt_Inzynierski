package com.podsadowski.warehouse.api.dto;

/**
 * One storage bin row for the inventory view (stocked or empty).
 */
public record InventoryBinResponse(
        Long locationId,
        String locationCode,
        String aisle,
        String rack,
        String bin,
        Long productId,
        String productSku,
        String productName,
        int quantity,
        Integer unitsPerBin,
        double fillPercent,
        boolean empty
) {
}
