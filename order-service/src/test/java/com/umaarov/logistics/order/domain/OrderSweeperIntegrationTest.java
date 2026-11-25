package com.umaarov.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umaarov.logistics.order.client.CancelResult;
import com.umaarov.logistics.order.client.DeliveryClient;
import com.umaarov.logistics.order.client.DownstreamUnavailableException;
import com.umaarov.logistics.order.client.TrackingClient;
import com.umaarov.logistics.order.client.WarehouseClient;
import com.umaarov.logistics.order.persistence.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class OrderSweeperIntegrationTest {

    @MockitoBean
    private WarehouseClient warehouse;

    @MockitoBean
    private DeliveryClient delivery;

    @MockitoBean
    private TrackingClient tracking;

    @Autowired
    private OrderSweeper sweeper;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private Clock clock;

    @BeforeEach
    void resetState() {
        orders.deleteAll();
        when(delivery.cancel(any())).thenReturn(CancelResult.NOT_FOUND);
    }

    private Order pendingOrder(Duration age) {
        PlaceOrderCommand command = new PlaceOrderCommand(
                null, "Aziz", "+998901234567", "Tashkent", "Street 1", List.of(new OrderLine("BOX-M", 1)));
        String tracking = "LG" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return orders.save(Order.pending(command, tracking, clock.instant().minus(age)));
    }

    private Order reload(Order order) {
        return orders.findById(order.getId()).orElseThrow();
    }

    @Test
    void stalePendingOrderIsRejectedAndCompensated() {
        Order stale = pendingOrder(Duration.ofMinutes(10));

        sweeper.rejectStalePendingOrders();

        Order stored = reload(stale);
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(stored.getRejectionReason()).isEqualTo("Order processing timed out");
        assertThat(stored.isCompensationPending()).isFalse();
        verify(warehouse).release(stale.getId());
        verify(delivery).cancel(stale.getId());
    }

    @Test
    void recentPendingOrderIsLeftAlone() {
        Order recent = pendingOrder(Duration.ofSeconds(10));

        sweeper.rejectStalePendingOrders();

        assertThat(reload(recent).getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(warehouse, never()).release(any());
    }

    @Test
    void staleOrderWithUnreachableWarehouseKeepsCompensationPending() {
        Order stale = pendingOrder(Duration.ofMinutes(10));
        doThrow(new DownstreamUnavailableException("warehouse-service is unreachable")).when(warehouse).release(any());

        sweeper.rejectStalePendingOrders();

        Order stored = reload(stale);
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(stored.isCompensationPending()).isTrue();
    }

    @Test
    void pendingCompensationIsRetriedUntilItSucceeds() {
        Order stale = pendingOrder(Duration.ofMinutes(10));
        doThrow(new DownstreamUnavailableException("warehouse-service is unreachable")).when(warehouse).release(any());
        sweeper.rejectStalePendingOrders();
        sweeper.retryPendingCompensations();
        assertThat(reload(stale).isCompensationPending()).isTrue();

        doNothing().when(warehouse).release(any());
        sweeper.retryPendingCompensations();

        assertThat(reload(stale).isCompensationPending()).isFalse();
    }

    @Test
    void settledOrdersAreNotRetried() {
        Order stale = pendingOrder(Duration.ofMinutes(10));
        sweeper.rejectStalePendingOrders();
        clearInvocations(warehouse, delivery);

        sweeper.sweep();

        verify(warehouse, never()).release(stale.getId());
    }

    @Test
    void trackingIsClosedWhenNoDeliveryWasEverCreated() {
        Order stale = pendingOrder(Duration.ofMinutes(10));

        sweeper.rejectStalePendingOrders();

        verify(tracking).record(argThat(
                request -> request.status().equals("CANCELLED")
                        && request.trackingNumber().equals(stale.getTrackingNumber())));
    }
}
