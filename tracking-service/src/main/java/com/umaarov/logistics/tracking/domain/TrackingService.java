package com.umaarov.logistics.tracking.domain;

import com.umaarov.logistics.tracking.persistence.TrackingEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingService {

    private static final Set<TrackingStatus> TERMINAL_STATUSES = Arrays.stream(TrackingStatus.values())
            .filter(TrackingStatus::isTerminal)
            .collect(Collectors.toUnmodifiableSet());

    private final TrackingEventRepository events;
    private final Clock clock;

    public TrackingService(TrackingEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public RecordResult record(NewTrackingEvent request) {
        var existing = events.findByEventId(request.eventId());
        if (existing.isPresent()) {
            return new RecordResult(existing.get(), false);
        }

        String trackingNumber = request.trackingNumber();
        boolean known = events.existsByTrackingNumber(trackingNumber);
        if (!known && request.status() != TrackingStatus.CREATED) {
            throw new InvalidTrackingTransitionException(
                    "The first event of parcel " + trackingNumber + " must be CREATED");
        }
        if (known && request.status() == TrackingStatus.CREATED) {
            throw new InvalidTrackingTransitionException("Parcel " + trackingNumber + " is already created");
        }
        if (known && events.existsByTrackingNumberAndStatusIn(trackingNumber, TERMINAL_STATUSES)) {
            throw new InvalidTrackingTransitionException("Parcel " + trackingNumber + " is already closed");
        }

        Instant now = clock.instant();
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : now;
        return new RecordResult(events.save(new TrackingEvent(request, occurredAt, now)), true);
    }

    @Transactional(readOnly = true)
    public List<TrackingEvent> timeline(String trackingNumber) {
        List<TrackingEvent> timeline = events.findByTrackingNumberOrderByOccurredAtAscRecordedAtAsc(trackingNumber);
        if (timeline.isEmpty()) {
            throw new TrackingNotFoundException(trackingNumber);
        }
        return timeline;
    }
}
