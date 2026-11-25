package com.umaarov.logistics.delivery.domain;

import java.util.Set;

public enum DeliveryStatus {
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    FAILED,
    CANCELLED;

    public Set<DeliveryStatus> allowedNext() {
        return switch (this) {
            case ASSIGNED -> Set.of(PICKED_UP, CANCELLED);
            case PICKED_UP -> Set.of(IN_TRANSIT);
            case IN_TRANSIT -> Set.of(DELIVERED, FAILED);
            case FAILED -> Set.of(IN_TRANSIT);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    public boolean canMoveTo(DeliveryStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isFinal() {
        return allowedNext().isEmpty();
    }
}
