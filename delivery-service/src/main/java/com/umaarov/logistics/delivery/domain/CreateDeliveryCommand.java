package com.umaarov.logistics.delivery.domain;

import java.util.UUID;

public record CreateDeliveryCommand(
        UUID orderId,
        String trackingNumber,
        String recipientName,
        String recipientPhone,
        String city,
        String addressLine) {
}
