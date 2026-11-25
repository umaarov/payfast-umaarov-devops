package com.umaarov.logistics.delivery.api;

import com.umaarov.logistics.delivery.domain.Courier;
import com.umaarov.logistics.delivery.domain.CourierStatus;
import java.time.Instant;
import java.util.UUID;

public record CourierResponse(
        UUID id,
        String name,
        String phone,
        CourierStatus status,
        int maxActiveDeliveries,
        int activeDeliveries,
        Instant createdAt) {

    public static CourierResponse from(Courier courier) {
        return new CourierResponse(
                courier.getId(),
                courier.getName(),
                courier.getPhone(),
                courier.getStatus(),
                courier.getMaxActiveDeliveries(),
                courier.getActiveDeliveries(),
                courier.getCreatedAt());
    }
}
