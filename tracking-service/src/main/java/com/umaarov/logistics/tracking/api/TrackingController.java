package com.umaarov.logistics.tracking.api;

import com.umaarov.logistics.tracking.domain.RecordResult;
import com.umaarov.logistics.tracking.domain.TrackingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/api/tracking/{trackingNumber}")
    public TimelineResponse timeline(
            @PathVariable @Pattern(regexp = TrackingNumberFormat.REGEX) String trackingNumber) {
        return TimelineResponse.from(trackingService.timeline(trackingNumber));
    }

    @PostMapping("/internal/tracking-events")
    public ResponseEntity<RecordedEventResponse> record(@Valid @RequestBody RecordEventRequest request) {
        RecordResult result = trackingService.record(request.toDomain());
        RecordedEventResponse body = RecordedEventResponse.from(result.event());
        return result.created() ? ResponseEntity.status(201).body(body) : ResponseEntity.ok(body);
    }
}
