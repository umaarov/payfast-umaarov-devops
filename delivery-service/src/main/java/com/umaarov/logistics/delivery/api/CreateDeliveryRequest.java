package com.umaarov.logistics.delivery.api;

import com.umaarov.logistics.delivery.domain.CreateDeliveryCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDeliveryRequest(
        @NotNull UUID orderId,
        @NotNull @Pattern(regexp = "[A-Z0-9]{8,32}") String trackingNumber,
        @NotBlank @Size(max = 120) String recipientName,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{9,15}") String recipientPhone,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 255) String addressLine) {

    public CreateDeliveryCommand toCommand() {
        return new CreateDeliveryCommand(orderId, trackingNumber, recipientName, recipientPhone, city, addressLine);
    }
}
