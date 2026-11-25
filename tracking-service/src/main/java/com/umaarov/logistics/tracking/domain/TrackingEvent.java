package com.umaarov.logistics.tracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracking_events")
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "tracking_number", nullable = false, length = 32)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TrackingStatus status;

    @Column(length = 500)
    private String description;

    @Column(length = 120)
    private String location;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected TrackingEvent() {
    }

    public TrackingEvent(NewTrackingEvent source, Instant occurredAt, Instant recordedAt) {
        this.eventId = source.eventId();
        this.trackingNumber = source.trackingNumber();
        this.status = source.status();
        this.description = source.description();
        this.location = source.location();
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public TrackingStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
