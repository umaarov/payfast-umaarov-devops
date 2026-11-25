package com.umaarov.logistics.order.domain;

import java.util.UUID;

public class IllegalOrderStateException extends RuntimeException {

    public IllegalOrderStateException(UUID orderId, OrderStatus status, String action) {
        super("Order " + orderId + " is " + status + " and cannot " + action);
    }
}
