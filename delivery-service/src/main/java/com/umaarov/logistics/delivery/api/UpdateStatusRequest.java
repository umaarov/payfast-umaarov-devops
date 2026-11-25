package com.umaarov.logistics.delivery.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.umaarov.logistics.delivery.domain.DeliveryStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStatusRequest(
        @NotNull DeliveryStatus status,
        @Size(max = 255) String note) {

    @JsonIgnore
    @AssertTrue(message = "note is required when status is FAILED")
    public boolean isFailureNoteProvided() {
        return status != DeliveryStatus.FAILED || (note != null && !note.isBlank());
    }
}
