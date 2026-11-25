package com.umaarov.logistics.tracking.api;

import com.umaarov.logistics.tracking.domain.NewTrackingEvent;
import com.umaarov.logistics.tracking.domain.TrackingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record RecordEventRequest(
        @NotNull UUID eventId,
        @NotNull @Pattern(regexp = TrackingNumberFormat.REGEX) String trackingNumber,
        @NotNull TrackingStatus status,
        @Size(max = 500) String description,
        @Size(max = 120) String location,
        Instant occurredAt) {

    public NewTrackingEvent toDomain() {
        return new NewTrackingEvent(eventId, trackingNumber, status, description, location, occurredAt);
    }
}
