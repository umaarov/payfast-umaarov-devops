package com.umaarov.logistics.tracking.api;

import com.umaarov.logistics.tracking.domain.TrackingEvent;
import com.umaarov.logistics.tracking.domain.TrackingStatus;
import java.time.Instant;
import java.util.UUID;

public record RecordedEventResponse(
        UUID eventId,
        String trackingNumber,
        TrackingStatus status,
        Instant occurredAt,
        Instant recordedAt) {

    public static RecordedEventResponse from(TrackingEvent event) {
        return new RecordedEventResponse(
                event.getEventId(),
                event.getTrackingNumber(),
                event.getStatus(),
                event.getOccurredAt(),
                event.getRecordedAt());
    }
}
