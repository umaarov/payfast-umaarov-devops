package com.umaarov.logistics.order.api;

import com.umaarov.logistics.order.domain.Order;
import com.umaarov.logistics.order.domain.OrderStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String trackingNumber,
        OrderStatus status,
        String deliveryStatus,
        String rejectionReason,
        String customerName,
        String customerPhone,
        String city,
        String addressLine,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(String sku, int quantity) {
    }

    public static OrderResponse from(Order order) {
        List<Item> items = order.getItems().stream()
                .map(item -> new Item(item.getSku(), item.getQuantity()))
                .sorted(Comparator.comparing(Item::sku))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getTrackingNumber(),
                order.getStatus(),
                order.getDeliveryStatus(),
                order.getRejectionReason(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getCity(),
                order.getAddressLine(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
