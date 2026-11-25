package com.umaarov.logistics.warehouse.domain;

import java.util.UUID;

public class IllegalReservationStateException extends RuntimeException {

    public IllegalReservationStateException(UUID orderId, ReservationStatus status, String action) {
        super("Reservation for order " + orderId + " is " + status + " and cannot be asked to " + action);
    }
}
