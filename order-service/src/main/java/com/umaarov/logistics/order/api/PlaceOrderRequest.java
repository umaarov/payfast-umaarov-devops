package com.umaarov.logistics.order.api;

import com.umaarov.logistics.order.domain.OrderLine;
import com.umaarov.logistics.order.domain.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaceOrderRequest(
        @NotBlank @Size(max = 120) String customerName,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{9,15}") String customerPhone,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 255) String addressLine,
        @NotEmpty @Size(max = 50) List<@Valid Item> items) {

    public record Item(
            @NotBlank @Size(max = 64) String sku,
            @Min(1) @Max(1000) int quantity) {
    }

    public PlaceOrderCommand toCommand(String idempotencyKey) {
        List<OrderLine> lines = items.stream().map(item -> new OrderLine(item.sku(), item.quantity())).toList();
        return new PlaceOrderCommand(idempotencyKey, customerName, customerPhone, city, addressLine, lines);
    }
}
