package com.umaarov.logistics.delivery.domain;

import java.util.UUID;

public class IllegalDeliveryTransitionException extends RuntimeException {

    public IllegalDeliveryTransitionException(UUID orderId, DeliveryStatus from, DeliveryStatus to) {
        super("Delivery of order " + orderId + " cannot move from " + from + " to " + to);
    }
}
