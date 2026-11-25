package com.umaarov.logistics.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.umaarov.logistics.delivery.outbox.OutboxEvent;
import com.umaarov.logistics.delivery.outbox.OutboxState;
import com.umaarov.logistics.delivery.outbox.OutboxTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DeliveryApiIntegrationTest extends ApiTestBase {

    private List<OutboxEvent> outbox(OutboxTarget target) {
        return outboxRepository.findAll().stream()
                .filter(event -> event.getTarget() == target)
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
    }

    @Test
    void deliveryGoesToLeastLoadedCourierWithTieBrokenByName() throws Exception {
        JsonNode alpha = createCourier("Alpha", 5);
        JsonNode bravo = createCourier("Bravo", 5);

        JsonNode first = createDeliveryOk(UUID.randomUUID());
        JsonNode second = createDeliveryOk(UUID.randomUUID());

        assertThat(first.get("courier").get("id")).isEqualTo(alpha.get("id"));
        assertThat(second.get("courier").get("id")).isEqualTo(bravo.get("id"));
        assertThat(first.get("status").asText()).isEqualTo("ASSIGNED");
    }

    @Test
    void offDutyCourierIsNeverAssigned() throws Exception {
        String offDuty = createCourier("Alpha", 5).get("id").asText();
        JsonNode onDuty = createCourier("Bravo", 5);
        mockMvc.perform(post("/api/delivery/couriers/" + offDuty + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isOk());

        JsonNode delivery = createDeliveryOk(UUID.randomUUID());

        assertThat(delivery.get("courier").get("id")).isEqualTo(onDuty.get("id"));
    }

    @Test
    void noCourierAvailableIsConflictAndCreatesNothing() throws Exception {
        UUID orderId = UUID.randomUUID();

        createDelivery(orderId).andExpect(status().isConflict());

        mockMvc.perform(get("/internal/deliveries/by-order/" + orderId)).andExpect(status().isNotFound());
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void courierCapacityIsRespectedAndFreedWhenDelivered() throws Exception {
        String courierId = createCourier("Alpha", 1).get("id").asText();
        JsonNode first = createDeliveryOk(UUID.randomUUID());

        createDelivery(UUID.randomUUID()).andExpect(status().isConflict());

        advance(first.get("id").asText(), "PICKED_UP", "IN_TRANSIT", "DELIVERED");
        mockMvc.perform(get("/api/delivery/couriers/" + courierId))
                .andExpect(jsonPath("$.activeDeliveries").value(0));
        createDelivery(UUID.randomUUID()).andExpect(status().isCreated());
    }

    @Test
    void repeatedCreationForSameOrderIsIdempotent() throws Exception {
        String courierId = createCourier("Alpha", 5).get("id").asText();
        UUID orderId = UUID.randomUUID();
        JsonNode first = createDeliveryOk(orderId);

        JsonNode second = json(createDelivery(orderId).andExpect(status().isOk()));

        assertThat(second.get("id")).isEqualTo(first.get("id"));
        mockMvc.perform(get("/api/delivery/couriers/" + courierId))
                .andExpect(jsonPath("$.activeDeliveries").value(1));
        assertThat(outbox(OutboxTarget.TRACKING)).hasSize(1);
    }

    @Test
    void creationQueuesCourierAssignedTrackingEvent() throws Exception {
        createCourier("Alpha", 5);
        JsonNode delivery = createDeliveryOk(UUID.randomUUID());

        List<OutboxEvent> tracking = outbox(OutboxTarget.TRACKING);

        assertThat(tracking).hasSize(1);
        OutboxEvent event = tracking.get(0);
        assertThat(event.getPath()).isEqualTo("/internal/tracking-events");
        assertThat(event.getState()).isEqualTo(OutboxState.PENDING);
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("trackingNumber")).isEqualTo(delivery.get("trackingNumber"));
        assertThat(payload.get("status").asText()).isEqualTo("COURIER_ASSIGNED");
        assertThat(payload.get("location").asText()).isEqualTo("Tashkent");
        assertThat(payload.get("eventId").asText()).isNotBlank();
        assertThat(outbox(OutboxTarget.ORDER)).isEmpty();
    }

    @Test
    void trackingPayloadNeverContainsPersonalData() throws Exception {
        createCourier("Alpha", 5);
        createDeliveryOk(UUID.randomUUID());

        String payload = outbox(OutboxTarget.TRACKING).get(0).getPayload();

        assertThat(payload).doesNotContain("Aziz").doesNotContain("+998901234567").doesNotContain("Amir Temur");
    }

    @Test
    void fullLifecycleQueuesTrackingAndOrderEvents() throws Exception {
        createCourier("Alpha", 5);
        UUID orderId = UUID.randomUUID();
        JsonNode delivery = createDeliveryOk(orderId);

        advance(delivery.get("id").asText(), "PICKED_UP", "IN_TRANSIT", "DELIVERED");

        List<String> trackingStatuses = new ArrayList<>();
        for (OutboxEvent event : outbox(OutboxTarget.TRACKING)) {
            trackingStatuses.add(objectMapper.readTree(event.getPayload()).get("status").asText());
        }
        assertThat(trackingStatuses)
                .containsExactly("COURIER_ASSIGNED", "PICKED_UP", "IN_TRANSIT", "DELIVERED");

        List<OutboxEvent> orderEvents = outbox(OutboxTarget.ORDER);
        assertThat(orderEvents).hasSize(3);
        assertThat(orderEvents.get(0).getPath()).isEqualTo("/internal/orders/" + orderId + "/delivery-events");
        List<String> orderStatuses = new ArrayList<>();
        for (OutboxEvent event : orderEvents) {
            orderStatuses.add(objectMapper.readTree(event.getPayload()).get("status").asText());
        }
        assertThat(orderStatuses).containsExactly("PICKED_UP", "IN_TRANSIT", "DELIVERED");
    }

    @Test
    void illegalTransitionIsConflictAndQueuesNothing() throws Exception {
        createCourier("Alpha", 5);
        JsonNode delivery = createDeliveryOk(UUID.randomUUID());
        long before = outboxRepository.count();

        updateStatus(delivery.get("id").asText(), "DELIVERED", null).andExpect(status().isConflict());

        assertThat(outboxRepository.count()).isEqualTo(before);
        mockMvc.perform(get("/api/delivery/deliveries/" + delivery.get("id").asText()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void repeatingTheCurrentStatusChangesNothing() throws Exception {
        createCourier("Alpha", 5);
        JsonNode delivery = createDeliveryOk(UUID.randomUUID());
        String id = delivery.get("id").asText();
        advance(id, "PICKED_UP");
        long before = outboxRepository.count();

        updateStatus(id, "PICKED_UP", null).andExpect(status().isOk());

        assertThat(outboxRepository.count()).isEqualTo(before);
    }

    @Test
    void finalStatusRejectsFurtherChanges() throws Exception {
        createCourier("Alpha", 5);
        String id = createDeliveryOk(UUID.randomUUID()).get("id").asText();
        advance(id, "PICKED_UP", "IN_TRANSIT", "DELIVERED");

        updateStatus(id, "IN_TRANSIT", null).andExpect(status().isConflict());
    }

    @Test
    void failedAttemptNeedsAReasonAndCanBeRetried() throws Exception {
        createCourier("Alpha", 5);
        String id = createDeliveryOk(UUID.randomUUID()).get("id").asText();
        advance(id, "PICKED_UP", "IN_TRANSIT");

        updateStatus(id, "FAILED", null).andExpect(status().isBadRequest());
        updateStatus(id, "FAILED", "Recipient not at home")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Recipient not at home"));
        updateStatus(id, "IN_TRANSIT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureReason").doesNotExist());
    }

    @Test
    void failedAttemptKeepsTheCourierSlotBusy() throws Exception {
        String courierId = createCourier("Alpha", 1).get("id").asText();
        String id = createDeliveryOk(UUID.randomUUID()).get("id").asText();
        advance(id, "PICKED_UP", "IN_TRANSIT");
        updateStatus(id, "FAILED", "No answer").andExpect(status().isOk());

        mockMvc.perform(get("/api/delivery/couriers/" + courierId))
                .andExpect(jsonPath("$.activeDeliveries").value(1));
    }

    @Test
    void cancellationBeforePickupFreesCourierAndIsIdempotent() throws Exception {
        String courierId = createCourier("Alpha", 1).get("id").asText();
        UUID orderId = UUID.randomUUID();
        createDeliveryOk(orderId);

        mockMvc.perform(post("/internal/deliveries/by-order/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/internal/deliveries/by-order/" + orderId + "/cancel"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/delivery/couriers/" + courierId))
                .andExpect(jsonPath("$.activeDeliveries").value(0));
        List<String> trackingStatuses = new ArrayList<>();
        for (OutboxEvent event : outbox(OutboxTarget.TRACKING)) {
            trackingStatuses.add(objectMapper.readTree(event.getPayload()).get("status").asText());
        }
        assertThat(trackingStatuses).containsExactly("COURIER_ASSIGNED", "CANCELLED");
        assertThat(outbox(OutboxTarget.ORDER)).isEmpty();
    }

    @Test
    void cancellationAfterPickupIsConflict() throws Exception {
        createCourier("Alpha", 5);
        UUID orderId = UUID.randomUUID();
        String id = createDeliveryOk(orderId).get("id").asText();
        advance(id, "PICKED_UP");

        mockMvc.perform(post("/internal/deliveries/by-order/" + orderId + "/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownDeliveryIsNotFound() throws Exception {
        mockMvc.perform(get("/api/delivery/deliveries/" + UUID.randomUUID())).andExpect(status().isNotFound());
        updateStatus(UUID.randomUUID().toString(), "PICKED_UP", null).andExpect(status().isNotFound());
        mockMvc.perform(post("/internal/deliveries/by-order/" + UUID.randomUUID() + "/cancel"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidDeliveryRequestIsRejected() throws Exception {
        mockMvc.perform(post("/internal/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"x\",\"recipientPhone\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(6));
    }

    @Test
    void unknownStatusValueIsRejected() throws Exception {
        createCourier("Alpha", 5);
        String id = createDeliveryOk(UUID.randomUUID()).get("id").asText();

        updateStatus(id, "TELEPORTED", null).andExpect(status().isBadRequest());
    }

    @Test
    void deliveriesCanBeFilteredByCourierAndStatus() throws Exception {
        String alpha = createCourier("Alpha", 5).get("id").asText();
        createCourier("Bravo", 5);
        String first = createDeliveryOk(UUID.randomUUID()).get("id").asText();
        createDeliveryOk(UUID.randomUUID());
        createDeliveryOk(UUID.randomUUID());
        advance(first, "PICKED_UP");

        mockMvc.perform(get("/api/delivery/deliveries").param("courierId", alpha))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/delivery/deliveries").param("status", "PICKED_UP"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first));
        mockMvc.perform(get("/api/delivery/deliveries").param("courierId", alpha).param("status", "ASSIGNED"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/delivery/deliveries")).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void deliveryCanBeReadByOrder() throws Exception {
        createCourier("Alpha", 5);
        UUID orderId = UUID.randomUUID();
        JsonNode delivery = createDeliveryOk(orderId);

        mockMvc.perform(get("/internal/deliveries/by-order/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(delivery.get("id").asText()))
                .andExpect(jsonPath("$.recipientName").value("Aziz Karimov"));
    }

    @Test
    void concurrentCreationsNeverExceedTotalCapacity() throws Exception {
        JsonNode alpha = createCourier("Alpha", 2);
        JsonNode bravo = createCourier("Bravo", 2);
        int attempts = 10;

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Callable<Integer> task = () -> {
                start.await();
                return createDelivery(UUID.randomUUID()).andReturn().getResponse().getStatus();
            };
            results.add(executor.submit(task));
        }
        start.countDown();

        int created = 0;
        int conflicts = 0;
        for (Future<Integer> result : results) {
            int httpStatus = result.get();
            if (httpStatus == 201) {
                created++;
            } else if (httpStatus == 409) {
                conflicts++;
            }
        }
        executor.shutdown();

        assertThat(created).isEqualTo(4);
        assertThat(conflicts).isEqualTo(attempts - 4);
        int alphaLoad = courierRepository.findById(UUID.fromString(alpha.get("id").asText()))
                .orElseThrow().getActiveDeliveries();
        int bravoLoad = courierRepository.findById(UUID.fromString(bravo.get("id").asText()))
                .orElseThrow().getActiveDeliveries();
        assertThat(alphaLoad).isEqualTo(2);
        assertThat(bravoLoad).isEqualTo(2);
    }
}
