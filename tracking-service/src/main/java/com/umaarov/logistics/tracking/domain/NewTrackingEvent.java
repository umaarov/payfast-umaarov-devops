package com.umaarov.logistics.tracking.domain;

import java.time.Instant;
import java.util.UUID;

public record NewTrackingEvent(
        UUID eventId,
        String trackingNumber,
        TrackingStatus status,
        String description,
        String location,
        Instant occurredAt) {
}
