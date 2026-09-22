package com.podsadowski.warehouse.domain.entity;

/**
 * Lifecycle of an inbound receipt through putaway.
 */
public enum InboundReceiptStatus {
    READY_TO_PUTAWAY,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
