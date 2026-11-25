package com.umaarov.logistics.delivery.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCourierRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{9,15}") String phone,
        @Min(1) @Max(50) Integer maxActiveDeliveries) {
}
