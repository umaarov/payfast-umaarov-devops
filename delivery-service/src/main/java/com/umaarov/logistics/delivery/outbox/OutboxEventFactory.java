package com.umaarov.logistics.delivery.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umaarov.logistics.delivery.domain.Delivery;
import com.umaarov.logistics.delivery.domain.DeliveryStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventFactory {

    private static final Set<DeliveryStatus> ORDER_NOTIFIED = EnumSet.of(
            DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED, DeliveryStatus.FAILED);

    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean notifiesOrder(DeliveryStatus status) {
        return ORDER_NOTIFIED.contains(status);
    }

    public OutboxEvent tracking(Delivery delivery, DeliveryStatus status, Instant now) {
        TrackingPayload payload = new TrackingPayload(
                UUID.randomUUID(),
                delivery.getTrackingNumber(),
                trackingStatus(status),
                description(status),
                delivery.getCity(),
                now);
        return new OutboxEvent(OutboxTarget.TRACKING, "/internal/tracking-events", json(payload), now);
    }

    public OutboxEvent order(Delivery delivery, DeliveryStatus status, Instant now) {
        OrderPayload payload = new OrderPayload(UUID.randomUUID(), delivery.getOrderId(), status.name(), now);
        return new OutboxEvent(
                OutboxTarget.ORDER,
                "/internal/orders/" + delivery.getOrderId() + "/delivery-events",
                json(payload),
                now);
    }

    private String trackingStatus(DeliveryStatus status) {
        return switch (status) {
            case ASSIGNED -> "COURIER_ASSIGNED";
            case PICKED_UP -> "PICKED_UP";
            case IN_TRANSIT -> "IN_TRANSIT";
            case DELIVERED -> "DELIVERED";
            case FAILED -> "DELIVERY_FAILED";
            case CANCELLED -> "CANCELLED";
        };
    }

    private String description(DeliveryStatus status) {
        return switch (status) {
            case ASSIGNED -> "Courier assigned";
            case PICKED_UP -> "Parcel picked up by courier";
            case IN_TRANSIT -> "Parcel is on the way";
            case DELIVERED -> "Parcel delivered";
            case FAILED -> "Delivery attempt failed";
            case CANCELLED -> "Delivery cancelled";
        };
    }

    private String json(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize outbox payload", exception);
        }
    }

    private record TrackingPayload(
            UUID eventId,
            String trackingNumber,
            String status,
            String description,
            String location,
            Instant occurredAt) {
    }

    private record OrderPayload(UUID eventId, UUID orderId, String status, Instant occurredAt) {
    }
}
