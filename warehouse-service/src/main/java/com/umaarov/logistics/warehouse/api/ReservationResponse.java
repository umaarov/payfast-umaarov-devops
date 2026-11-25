package com.umaarov.logistics.warehouse.api;

import com.umaarov.logistics.warehouse.domain.Reservation;
import com.umaarov.logistics.warehouse.domain.ReservationStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID orderId,
        ReservationStatus status,
        List<Line> lines,
        Instant createdAt,
        Instant updatedAt) {

    public record Line(String sku, int quantity) {
    }

    public static ReservationResponse from(Reservation reservation) {
        List<Line> lines = reservation.getLines().stream()
                .map(line -> new Line(line.getProduct().getSku(), line.getQuantity()))
                .sorted(Comparator.comparing(Line::sku))
                .toList();
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                lines,
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
