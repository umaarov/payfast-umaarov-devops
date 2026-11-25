package com.umaarov.logistics.warehouse.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReceiptRequest(@Min(1) @Max(1_000_000) int quantity) {
}
