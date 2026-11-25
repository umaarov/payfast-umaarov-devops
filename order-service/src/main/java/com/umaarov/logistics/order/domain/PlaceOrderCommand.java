package com.umaarov.logistics.order.domain;

import java.util.List;

public record PlaceOrderCommand(
        String idempotencyKey,
        String customerName,
        String customerPhone,
        String city,
        String addressLine,
        List<OrderLine> lines) {
}
