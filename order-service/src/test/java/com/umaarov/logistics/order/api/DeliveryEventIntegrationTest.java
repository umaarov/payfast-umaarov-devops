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
import com.umaarov.logistics.order.client.DownstreamUnavailableException;
import com.umaarov.logistics.order.domain.Order;
import com.umaarov.logistics.order.domain.OrderStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DeliveryEventIntegrationTest extends ApiTestBase {

    private Order stored(String id) {
        return orderRepository.findById(UUID.fromString(id)).orElseThrow();
    }

    @Test
    void pickUpCommitsStockAndMovesOrderIntoDelivery() throws Exception {
        String id = placeConfirmedOrder().get("id").asText();

        deliveryEvent(id, "PICKED_UP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_DELIVERY"))
                .andExpect(jsonPath("$.deliveryStatus").value("PICKED_UP"));

        verify(warehouse).commit(UUID.fromString(id));
    }

    @Test
    void transitAndFailedAttemptKeepTheOrderInDeliveryWithoutTouchingStock() throws Exception {
        String id = placeConfirmedOrder().get("id").asText();
        deliveryEvent(id, "PICKED_UP").andExpect(status().isOk());

        deliveryEvent(id, "IN_TRANSIT")
                .andExpect(jsonPath("$.status").value("IN_DELIVERY"))
                .andExpect(jsonPath("$.deliveryStatus").value("IN_TRANSIT"));
        deliveryEvent(id, "FAILED")
                .andExpect(jsonPath("$.status").value("IN_DELIVERY"))
                .andExpect(jsonPath("$.deliveryStatus").value("FAILED"));

        verify(warehouse, times(1)).commit(any());
    }

    @Test
    void deliveredEventFinishesTheOrder() throws Exception {
        String id = placeConfirmedOrder().get("id").asText();
        deliveryEvent(id, "PICKED_UP").andExpect(status().isOk());
        deliveryEvent(id, "IN_TRANSIT").andExpect(status().isOk());

        deliveryEvent(id, "DELIVERED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        assertThat(stored(id).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void deliveredWithoutEarlierPickUpEventStillCommitsStock() throws Exception {
        String id = placeConfirmedOrder().get("id").asText();

        deliveryEvent(id, "DELIVERED").andExpect(status().isOk());

        verify(warehouse).commit(UUID.fromString(id));
    }

    @Test
    void repeatedPickUpEventIsHarmless() throws Exception {
        String id = placeConfirmedOrder().get("id").asText();

        deliveryEvent(id, "PICKED_UP").andExpect(status().isOk());
        deliveryEvent(id, "PICKED_UP").andExpect(status().isOk());

        assertThat(stored(id).getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
    }

    @Test
    void stockOutageAnswersServiceUnavailableAndLeavesOrderUntouched() throws Exception {
        String id = placeConfirmedOrder().get("id").asText();
        doThrow(new DownstreamUnavailableException("warehouse-service is unreachable")).when(warehouse).commit(any());

        deliveryEvent(id, "PICKED_UP").andExpect(status().isServiceUnavailable());

        assertThat(stored(id).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void lateEventsAfterDeliveryAreIgnored() throws Exception {
        String id = placeConfirmedOrder().get("id").asText();
        deliveryEvent(id, "DELIVERED").andExpect(status().isOk());

        deliveryEvent(id, "IN_TRANSIT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        verify(warehouse, times(1)).commit(any());
    }

    @Test
    void eventForCancelledOrderIsConflict() throws Exception {
        JsonNode order = placeConfirmedOrder();
        String id = order.get("id").asText();
        mockMvc.perform(post("/api/orders/" + id + "/cancel")).andExpect(status().isOk());

        deliveryEvent(id, "PICKED_UP").andExpect(status().isConflict());

        verify(warehouse, never()).commit(any());
    }

    @Test
    void eventForUnknownOrderIsNotFound() throws Exception {
        deliveryEvent(UUID.randomUUID().toString(), "PICKED_UP").andExpect(status().isNotFound());
    }

    @Test
    void unsupportedOrMissingStatusIsRejected() throws Exception {
        String id = placeConfirmedOrder().get("id").asText();

        deliveryEvent(id, "ASSIGNED").andExpect(status().isBadRequest());
        mockMvc.perform(post("/internal/orders/" + id + "/delivery-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
