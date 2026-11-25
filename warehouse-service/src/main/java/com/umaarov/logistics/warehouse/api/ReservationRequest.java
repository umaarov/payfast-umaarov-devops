package com.umaarov.logistics.warehouse.api;

import com.umaarov.logistics.warehouse.domain.ReservationItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ReservationRequest(
        @NotNull UUID orderId,
        @NotEmpty @Size(max = 100) List<@Valid Item> items) {

    public record Item(
            @NotBlank @Size(max = 64) String sku,
            @Min(1) @Max(1_000_000) int quantity) {

        ReservationItem toDomain() {
            return new ReservationItem(sku, quantity);
        }
    }

    public List<ReservationItem> toDomainItems() {
        return items.stream().map(Item::toDomain).toList();
    }
}
