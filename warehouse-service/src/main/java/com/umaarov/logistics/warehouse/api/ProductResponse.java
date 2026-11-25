package com.umaarov.logistics.warehouse.api;

import com.umaarov.logistics.warehouse.domain.Product;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        int quantityOnHand,
        int quantityReserved,
        int available,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getQuantityOnHand(),
                product.getQuantityReserved(),
                product.getAvailable(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
