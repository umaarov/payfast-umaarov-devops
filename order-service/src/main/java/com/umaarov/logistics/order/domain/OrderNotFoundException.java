package com.umaarov.logistics.order.domain;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("Order " + orderId + " does not exist");
    }
}
