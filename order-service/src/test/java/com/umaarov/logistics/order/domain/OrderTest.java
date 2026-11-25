package com.umaarov.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderTest {

    private final Instant now = Instant.parse("2026-11-25T08:00:00Z");
    private final Instant later = Instant.parse("2026-11-25T09:00:00Z");

    private Order newOrder() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "key", "Aziz", "+998901234567", "Tashkent", "Street 1",
                List.of(new OrderLine("BOX-M", 2), new OrderLine("TAPE-1", 1), new OrderLine("BOX-M", 3)));
        return Order.pending(command, "LGK4M9X2PQ7A", now);
    }

    @Test
    void newOrderIsPendingAndMergesDuplicateSkus() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.lines()).containsExactly(new OrderLine("BOX-M", 5), new OrderLine("TAPE-1", 1));
        assertThat(order.isCompensationPending()).isFalse();
    }

    @Test
    void pendingOrderCanBeConfirmedOrRejectedButNotBoth() {
        Order confirmed = newOrder();
        confirmed.confirm(later);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> confirmed.reject("late", false, later))
                .isInstanceOf(IllegalOrderStateException.class);

        Order rejected = newOrder();
        rejected.reject("No stock", true, later);
        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("No stock");
        assertThat(rejected.isCompensationPending()).isTrue();
        assertThatThrownBy(() -> rejected.confirm(later)).isInstanceOf(IllegalOrderStateException.class);
    }

    @Test
    void longRejectionReasonIsTruncated() {
        Order order = newOrder();

        order.reject("x".repeat(400), false, later);

        assertThat(order.getRejectionReason()).hasSize(255);
    }

    @Test
    void onlyConfirmedOrderCanBeCancelled() {
        Order pending = newOrder();
        assertThatThrownBy(() -> pending.cancel(false, later)).isInstanceOf(IllegalOrderStateException.class);

        Order confirmed = newOrder();
        confirmed.confirm(now);
        confirmed.cancel(true, later);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(confirmed.isCompensationPending()).isTrue();
    }

    @Test
    void deliveryEventsMoveConfirmedOrderThroughDelivery() {
        Order order = newOrder();
        order.confirm(now);

        order.applyDeliveryStatus(DeliveryEventStatus.PICKED_UP, later);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
        order.applyDeliveryStatus(DeliveryEventStatus.FAILED, later);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
        assertThat(order.getDeliveryStatus()).isEqualTo("FAILED");
        order.applyDeliveryStatus(DeliveryEventStatus.DELIVERED, later);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void deliveredOrderIgnoresLateEvents() {
        Order order = newOrder();
        order.confirm(now);
        order.applyDeliveryStatus(DeliveryEventStatus.DELIVERED, later);

        order.applyDeliveryStatus(DeliveryEventStatus.IN_TRANSIT, later);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveryStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void cancelledOrderRejectsDeliveryEvents() {
        Order order = newOrder();
        order.confirm(now);
        order.cancel(false, later);

        assertThat(order.acceptsDeliveryEvents()).isFalse();
        assertThatThrownBy(() -> order.applyDeliveryStatus(DeliveryEventStatus.PICKED_UP, later))
                .isInstanceOf(IllegalOrderStateException.class);
    }

    @Test
    void trackingNumbersHaveTheDocumentedShapeAndDoNotRepeat() {
        TrackingNumberGenerator generator = new TrackingNumberGenerator();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            String number = generator.next();
            assertThat(number).matches("LG[A-Z2-9]{10}");
            seen.add(number);
        }

        assertThat(seen).hasSize(1000);
    }
}
