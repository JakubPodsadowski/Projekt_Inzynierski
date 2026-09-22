package com.podsadowski.warehouse.wms;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbound demand message as received from an external warehouse / OMS system.
 * Lines are SKU-based (external identity), not internal product IDs.
 */
public record ExternalDemandMessage(
        String externalRef,
        boolean vip,
        LocalDateTime dueAt,
        List<ExternalLine> lines
) {
    public record ExternalLine(String sku, int quantity) {
    }
}
