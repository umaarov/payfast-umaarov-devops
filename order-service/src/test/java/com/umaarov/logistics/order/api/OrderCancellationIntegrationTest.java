package com.umaarov.logistics.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.umaarov.logistics.order.client.DeliveryNotCancellableException;
import com.umaarov.logistics.order.client.DownstreamUnavailableException;
import com.umaarov.logistics.order.client.InsufficientStockException;
import com.umaarov.logistics.order.domain.Order;
import com.umaarov.logistics.order.domain.OrderStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCancellationIntegrationTest extends ApiTestBase {

    private Order stored(JsonNode order) {
        return orderRepository.findById(UUID.fromString(order.get("id").asText())).orElseThrow();
    }

    @Test
    void confirmedOrderCanBeCancelledAndStockIsReleased() throws Exception {
        JsonNode order = placeConfirmedOrder();
        UUID orderId = UUID.fromString(order.get("id").asText());

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(delivery).cancel(orderId);
        verify(warehouse).release(orderId);
        assertThat(stored(order).isCompensationPending()).isFalse();
    }

    @Test
    void cancellingTwiceDoesNotRepeatSideEffects() throws Exception {
        JsonNode order = placeConfirmedOrder();
        String id = order.get("id").asText();
        mockMvc.perform(post("/api/orders/" + id + "/cancel")).andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(delivery, times(1)).cancel(any());
        verify(warehouse, times(1)).release(any());
    }

    @Test
    void pickedUpParcelCannotBeCancelled() throws Exception {
        JsonNode order = placeConfirmedOrder();
        doThrow(new DeliveryNotCancellableException()).when(delivery).cancel(any());

        mockMvc.perform(post("/api/orders/" + order.get("id").asText() + "/cancel"))
                .andExpect(status().isConflict());

        assertThat(stored(order).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(warehouse, never()).release(any());
    }

    @Test
    void deliveryOutageLeavesOrderConfirmed() throws Exception {
        JsonNode order = placeConfirmedOrder();
        doThrow(new DownstreamUnavailableException("delivery-service is unreachable")).when(delivery).cancel(any());

        mockMvc.perform(post("/api/orders/" + order.get("id").asText() + "/cancel"))
                .andExpect(status().isServiceUnavailable());

        assertThat(stored(order).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void failedStockReleaseStillCancelsAndIsRemembered() throws Exception {
        JsonNode order = placeConfirmedOrder();
        doThrow(new DownstreamUnavailableException("warehouse-service is unreachable"))
                .when(warehouse).release(any());

        mockMvc.perform(post("/api/orders/" + order.get("id").asText() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Order stored = stored(order);
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stored.isCompensationPending()).isTrue();
    }

    @Test
    void orderInDeliveryCannotBeCancelled() throws Exception {
        JsonNode order = placeConfirmedOrder();
        String id = order.get("id").asText();
        deliveryEvent(id, "PICKED_UP").andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + id + "/cancel")).andExpect(status().isConflict());

        verify(delivery, never()).cancel(any());
    }

    @Test
    void rejectedOrderCannotBeCancelled() throws Exception {
        doThrow(new InsufficientStockException("BOX-M", 5, 1)).when(warehouse).reserve(any(), any());
        JsonNode problem = json(placeOrder(null, Map.of("BOX-M", 5)).andExpect(status().isConflict()));

        mockMvc.perform(post("/api/orders/" + problem.get("orderId").asText() + "/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownOrderCannotBeCancelled() throws Exception {
        mockMvc.perform(post("/api/orders/" + UUID.randomUUID() + "/cancel")).andExpect(status().isNotFound());
    }
}
