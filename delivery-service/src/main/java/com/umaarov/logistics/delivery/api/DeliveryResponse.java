package com.umaarov.logistics.delivery.api;

import com.umaarov.logistics.delivery.domain.Delivery;
import com.umaarov.logistics.delivery.domain.DeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID orderId,
        String trackingNumber,
        DeliveryStatus status,
        CourierSummary courier,
        String recipientName,
        String recipientPhone,
        String city,
        String addressLine,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public record CourierSummary(UUID id, String name, String phone) {
    }

    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getTrackingNumber(),
                delivery.getStatus(),
                new CourierSummary(
                        delivery.getCourier().getId(),
                        delivery.getCourier().getName(),
                        delivery.getCourier().getPhone()),
                delivery.getRecipientName(),
                delivery.getRecipientPhone(),
                delivery.getCity(),
                delivery.getAddressLine(),
                delivery.getFailureReason(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt());
    }
}
