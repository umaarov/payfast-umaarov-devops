package com.umaarov.logistics.order.client;

import java.util.UUID;

public record DeliveryRequest(
        UUID orderId,
        String trackingNumber,
        String recipientName,
        String recipientPhone,
        String city,
        String addressLine) {
}
