package com.umaarov.logistics.order.domain;

import java.util.UUID;

public class OrderRejectedException extends RuntimeException {

    private final UUID orderId;
    private final RejectionReason reason;

    public OrderRejectedException(UUID orderId, RejectionReason reason, String detail) {
        super(detail);
        this.orderId = orderId;
        this.reason = reason;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public RejectionReason getReason() {
        return reason;
    }
}
