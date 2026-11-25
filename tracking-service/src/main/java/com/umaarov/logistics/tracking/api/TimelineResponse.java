package com.umaarov.logistics.tracking.api;

import com.umaarov.logistics.tracking.domain.TrackingEvent;
import com.umaarov.logistics.tracking.domain.TrackingStatus;
import java.time.Instant;
import java.util.List;

public record TimelineResponse(
        String trackingNumber,
        TrackingStatus status,
        Instant updatedAt,
        List<Entry> events) {

    public record Entry(TrackingStatus status, String description, String location, Instant occurredAt) {
    }

    public static TimelineResponse from(List<TrackingEvent> timeline) {
        TrackingEvent latest = timeline.get(timeline.size() - 1);
        List<Entry> entries = timeline.stream()
                .map(event -> new Entry(
                        event.getStatus(), event.getDescription(), event.getLocation(), event.getOccurredAt()))
                .toList();
        return new TimelineResponse(latest.getTrackingNumber(), latest.getStatus(), latest.getOccurredAt(), entries);
    }
}
