package com.umaarov.logistics.warehouse.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String sku,
        @NotBlank @Size(max = 255) String name,
        @Min(0) @Max(1_000_000) int initialQuantity) {
}
