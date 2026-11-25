package com.umaarov.logistics.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryStatusTest {

    private final Instant now = Instant.parse("2026-11-25T08:00:00Z");

    private Delivery newDelivery() {
        Courier courier = new Courier("Alpha", "+998901112233", 5, now);
        CreateDeliveryCommand command = new CreateDeliveryCommand(
                UUID.randomUUID(), "LG1234567890", "Aziz", "+998901234567", "Tashkent", "Street 1");
        return new Delivery(command, courier, now);
    }

    @Test
    void happyPathIsAllowed() {
        assertThat(DeliveryStatus.ASSIGNED.canMoveTo(DeliveryStatus.PICKED_UP)).isTrue();
        assertThat(DeliveryStatus.PICKED_UP.canMoveTo(DeliveryStatus.IN_TRANSIT)).isTrue();
        assertThat(DeliveryStatus.IN_TRANSIT.canMoveTo(DeliveryStatus.DELIVERED)).isTrue();
    }

    @Test
    void stepsCannotBeSkippedOrReversed() {
        assertThat(DeliveryStatus.ASSIGNED.canMoveTo(DeliveryStatus.DELIVERED)).isFalse();
        assertThat(DeliveryStatus.ASSIGNED.canMoveTo(DeliveryStatus.IN_TRANSIT)).isFalse();
        assertThat(DeliveryStatus.IN_TRANSIT.canMoveTo(DeliveryStatus.PICKED_UP)).isFalse();
        assertThat(DeliveryStatus.PICKED_UP.canMoveTo(DeliveryStatus.CANCELLED)).isFalse();
    }

    @Test
    void failedDeliveryCanOnlyBeRetried() {
        assertThat(DeliveryStatus.FAILED.allowedNext()).containsExactly(DeliveryStatus.IN_TRANSIT);
        assertThat(DeliveryStatus.FAILED.isFinal()).isFalse();
    }

    @Test
    void deliveredAndCancelledAreFinal() {
        assertThat(DeliveryStatus.DELIVERED.isFinal()).isTrue();
        assertThat(DeliveryStatus.CANCELLED.isFinal()).isTrue();
        assertThat(DeliveryStatus.ASSIGNED.isFinal()).isFalse();
    }

    @Test
    void deliveryRejectsIllegalMove() {
        Delivery delivery = newDelivery();

        assertThatThrownBy(() -> delivery.moveTo(DeliveryStatus.DELIVERED, null, now))
                .isInstanceOf(IllegalDeliveryTransitionException.class);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
    }

    @Test
    void failureReasonIsKeptOnlyWhileFailed() {
        Delivery delivery = newDelivery();
        delivery.moveTo(DeliveryStatus.PICKED_UP, null, now);
        delivery.moveTo(DeliveryStatus.IN_TRANSIT, null, now);

        delivery.moveTo(DeliveryStatus.FAILED, "No answer", now);
        assertThat(delivery.getFailureReason()).isEqualTo("No answer");

        delivery.moveTo(DeliveryStatus.IN_TRANSIT, null, now);
        assertThat(delivery.getFailureReason()).isNull();
    }
}
