package com.podsadowski.warehouse.domain.entity;

/**
 * Lifecycle of an outbound customer order through pick → pack.
 */
public enum OutboundOrderStatus {
    READY_TO_PICK,
    PICKING,
    READY_TO_PACK,
    PACKING,
    PACKED,
    CANCELLED
}
