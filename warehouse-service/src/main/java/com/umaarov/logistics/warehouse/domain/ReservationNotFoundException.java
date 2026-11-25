package com.umaarov.logistics.warehouse.domain;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(UUID orderId) {
        super("No reservation exists for order " + orderId);
    }
}
