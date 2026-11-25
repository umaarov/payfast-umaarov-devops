package com.umaarov.logistics.tracking.domain;

public enum TrackingStatus {
    CREATED,
    COURIER_ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    DELIVERY_FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
