package com.umaarov.logistics.order.api;

import com.umaarov.logistics.order.domain.DeliveryEventStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record DeliveryEventRequest(
        @NotNull UUID eventId,
        @NotNull DeliveryEventStatus status,
        Instant occurredAt) {
}
