package com.podsadowski.warehouse.domain.entity;

/**
 * Types of per-employee operational events for the activity timeline.
 */
public enum WorkActivityType {
    PICK_STARTED,
    PICK_ITEM,
    PICK_COMPLETED,
    PACK_STARTED,
    PACK_PROGRESS,
    PACK_COMPLETED,
    PUTAWAY_STARTED,
    PUTAWAY_ITEM,
    PUTAWAY_COMPLETED
}
