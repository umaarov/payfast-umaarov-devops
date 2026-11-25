package com.umaarov.logistics.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.umaarov.logistics.order.client.DeliveryRequest;
import com.umaarov.logistics.order.client.DownstreamUnavailableException;
import com.umaarov.logistics.order.client.InsufficientStockException;
import com.umaarov.logistics.order.client.NoCourierAvailableException;
import com.umaarov.logistics.order.client.TrackingEventRequest;
import com.umaarov.logistics.order.client.UnknownProductException;
import com.umaarov.logistics.order.domain.Order;
import com.umaarov.logistics.order.domain.OrderLine;
import com.umaarov.logistics.order.domain.OrderStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class OrderPlacementIntegrationTest extends ApiTestBase {

    private Order storedOrder(String id) {
        return orderRepository.findById(UUID.fromString(id)).orElseThrow();
    }

    @Test
    void placedOrderIsConfirmedWithTrackingNumber() throws Exception {
        placeOrder(null, Map.of("BOX-M", 2, "TAPE-1", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.trackingNumber").value(matchesPattern("LG[A-Z2-9]{10}")))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sku").value("BOX-M"))
                .andExpect(jsonPath("$.customerName").value("Aziz Karimov"));
    }

    @Test
    void stepsRunInOrderTrackingThenStockThenDelivery() throws Exception {
        JsonNode order = json(placeOrder(null, Map.of("BOX-M", 2)).andExpect(status().isCreated()));
        UUID orderId = UUID.fromString(order.get("id").asText());

        var inOrder = inOrder(tracking, warehouse, delivery);
        ArgumentCaptor<TrackingEventRequest> created = ArgumentCaptor.forClass(TrackingEventRequest.class);
        inOrder.verify(tracking).record(created.capture());
        inOrder.verify(warehouse).reserve(any(), any());
        ArgumentCaptor<DeliveryRequest> deliveryRequest = ArgumentCaptor.forClass(DeliveryRequest.class);
        inOrder.verify(delivery).create(deliveryRequest.capture());

        assertThat(created.getValue().status()).isEqualTo("CREATED");
        assertThat(created.getValue().trackingNumber()).isEqualTo(order.get("trackingNumber").asText());
        assertThat(created.getValue().location()).isEqualTo("Tashkent");
        assertThat(deliveryRequest.getValue().orderId()).isEqualTo(orderId);
        assertThat(deliveryRequest.getValue().trackingNumber()).isEqualTo(order.get("trackingNumber").asText());
        assertThat(deliveryRequest.getValue().recipientName()).isEqualTo("Aziz Karimov");
        assertThat(deliveryRequest.getValue().addressLine()).isEqualTo("Amir Temur 1, apt 5");
    }

    @Test
    void trackingEventCarriesNoPersonalData() throws Exception {
        placeOrder(null, Map.of("BOX-M", 1)).andExpect(status().isCreated());

        ArgumentCaptor<TrackingEventRequest> created = ArgumentCaptor.forClass(TrackingEventRequest.class);
        verify(tracking).record(created.capture());

        assertThat(created.getValue().toString())
                .doesNotContain("Aziz")
                .doesNotContain("+998901234567")
                .doesNotContain("Amir Temur");
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateSkusInOneOrderAreMerged() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerName", "Aziz",
                                "customerPhone", "+998901234567",
                                "city", "Tashkent",
                                "addressLine", "Street 1",
                                "items", List.of(
                                        Map.of("sku", "BOX-M", "quantity", 2),
                                        Map.of("sku", "BOX-M", "quantity", 3))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5));

        ArgumentCaptor<List<OrderLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(warehouse).reserve(any(), lines.capture());
        assertThat(lines.getValue()).containsExactly(new OrderLine("BOX-M", 5));
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheSameOrderWithoutNewSideEffects() throws Exception {
        JsonNode first = json(placeOrder("key-1", Map.of("BOX-M", 2)).andExpect(status().isCreated()));

        JsonNode second = json(placeOrder("key-1", Map.of("BOX-M", 2)).andExpect(status().isOk()));

        assertThat(second.get("id")).isEqualTo(first.get("id"));
        verify(warehouse, times(1)).reserve(any(), any());
        verify(delivery, times(1)).create(any());
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void differentIdempotencyKeysCreateDifferentOrders() throws Exception {
        JsonNode first = json(placeOrder("key-a", Map.of("BOX-M", 1)).andExpect(status().isCreated()));
        JsonNode second = json(placeOrder("key-b", Map.of("BOX-M", 1)).andExpect(status().isCreated()));

        assertThat(second.get("id")).isNotEqualTo(first.get("id"));
        assertThat(second.get("trackingNumber")).isNotEqualTo(first.get("trackingNumber"));
    }

    @Test
    void tooLongIdempotencyKeyIsRejected() throws Exception {
        placeOrder("k".repeat(65), Map.of("BOX-M", 1)).andExpect(status().isBadRequest());

        verify(warehouse, never()).reserve(any(), any());
    }

    @Test
    void insufficientStockRejectsOrderAndCompensates() throws Exception {
        doThrow(new InsufficientStockException("BOX-M", 5, 1)).when(warehouse).reserve(any(), any());

        JsonNode problem = json(placeOrder(null, Map.of("BOX-M", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_STOCK")));

        UUID orderId = UUID.fromString(problem.get("orderId").asText());
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).contains("BOX-M");
        assertThat(order.isCompensationPending()).isFalse();
        verify(delivery, never()).create(any());
        verify(warehouse).release(orderId);
        verify(delivery).cancel(orderId);
        ArgumentCaptor<TrackingEventRequest> events = ArgumentCaptor.forClass(TrackingEventRequest.class);
        verify(tracking, times(2)).record(events.capture());
        assertThat(events.getAllValues()).extracting(TrackingEventRequest::status)
                .containsExactly("CREATED", "CANCELLED");
    }

    @Test
    void unknownProductRejectsOrderWithUnprocessableEntity() throws Exception {
        doThrow(new UnknownProductException("Product with sku NOPE does not exist"))
                .when(warehouse).reserve(any(), any());

        placeOrder(null, Map.of("NOPE", 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("UNKNOWN_PRODUCT"));

        assertThat(orderRepository.findAll()).singleElement()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED));
    }

    @Test
    void noCourierRejectsOrderAndReleasesStock() throws Exception {
        doThrow(new NoCourierAvailableException()).when(delivery).create(any());

        JsonNode problem = json(placeOrder(null, Map.of("BOX-M", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("NO_COURIER_AVAILABLE")));

        UUID orderId = UUID.fromString(problem.get("orderId").asText());
        verify(warehouse).reserve(any(), any());
        verify(warehouse).release(orderId);
        assertThat(storedOrder(orderId.toString()).getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void deliveryOutageRejectsOrderWithServiceUnavailable() throws Exception {
        doThrow(new DownstreamUnavailableException("delivery-service is unreachable"))
                .when(delivery).create(any());

        JsonNode problem = json(placeOrder(null, Map.of("BOX-M", 1))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("SERVICE_UNAVAILABLE")));

        UUID orderId = UUID.fromString(problem.get("orderId").asText());
        verify(warehouse).release(orderId);
        assertThat(storedOrder(orderId.toString()).getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void trackingOutageStopsTheFlowBeforeStockIsTouched() throws Exception {
        doThrow(new DownstreamUnavailableException("tracking-service is unreachable"))
                .when(tracking).record(any());

        JsonNode problem = json(placeOrder(null, Map.of("BOX-M", 1)).andExpect(status().isServiceUnavailable()));

        verify(warehouse, never()).reserve(any(), any());
        verify(delivery, never()).create(any());
        Order order = storedOrder(problem.get("orderId").asText());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.isCompensationPending()).isTrue();
    }

    @Test
    void failedStockReleaseIsRememberedForTheSweeper() throws Exception {
        doThrow(new DownstreamUnavailableException("warehouse-service is unreachable"))
                .when(warehouse).release(any());
        doThrow(new DownstreamUnavailableException("delivery-service is unreachable"))
                .when(delivery).create(any());

        JsonNode problem = json(placeOrder(null, Map.of("BOX-M", 1)).andExpect(status().isServiceUnavailable()));

        assertThat(storedOrder(problem.get("orderId").asText()).isCompensationPending()).isTrue();
    }

    @Test
    void replayOfRejectedOrderWithSameKeyReturnsTheRejectedOrder() throws Exception {
        doThrow(new InsufficientStockException("BOX-M", 5, 1)).when(warehouse).reserve(any(), any());
        placeOrder("key-rejected", Map.of("BOX-M", 5)).andExpect(status().isConflict());

        placeOrder("key-rejected", Map.of("BOX-M", 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(warehouse, times(1)).reserve(any(), any());
    }

    @Test
    void invalidOrderIsRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"\",\"customerPhone\":\"1\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(5));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void invalidItemQuantityIsRejected() throws Exception {
        placeOrder(null, Map.of("BOX-M", 0)).andExpect(status().isBadRequest());
        placeOrder(null, Map.of("BOX-M", 5000)).andExpect(status().isBadRequest());
    }

    @Test
    void orderCanBeReadAndListed() throws Exception {
        JsonNode order = placeConfirmedOrder();
        placeOrder(null, Map.of("BOX-M", 1)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders/" + order.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(order.get("trackingNumber").asText()));
        mockMvc.perform(get("/api/orders")).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/orders").param("status", "CANCELLED")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/orders").param("status", "CONFIRMED")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
