package com.umaarov.logistics.tracking.persistence;

import com.umaarov.logistics.tracking.domain.TrackingEvent;
import com.umaarov.logistics.tracking.domain.TrackingStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {

    Optional<TrackingEvent> findByEventId(UUID eventId);

    boolean existsByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumberAndStatusIn(String trackingNumber, Collection<TrackingStatus> statuses);

    List<TrackingEvent> findByTrackingNumberOrderByOccurredAtAscRecordedAtAsc(String trackingNumber);
}
