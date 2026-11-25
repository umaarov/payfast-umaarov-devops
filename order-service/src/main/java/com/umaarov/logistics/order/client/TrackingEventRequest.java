package com.umaarov.logistics.order.client;

import java.util.UUID;

public record TrackingEventRequest(
        UUID eventId,
        String trackingNumber,
        String status,
        String description,
        String location) {
}
