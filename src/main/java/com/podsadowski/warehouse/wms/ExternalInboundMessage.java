package com.podsadowski.warehouse.wms;

import java.util.List;

/**
 * Inbound shipment message as received from an external WMS / supplier feed.
 */
public record ExternalInboundMessage(
        String externalRef,
        List<ExternalLine> lines
) {
    public record ExternalLine(String sku, int quantity, String preferredLocationCode) {
        public ExternalLine(String sku, int quantity) {
            this(sku, quantity, null);
        }
    }
}
