package com.umaarov.logistics.delivery.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umaarov.logistics.delivery.outbox.OutboxRepository;
import com.umaarov.logistics.delivery.persistence.CourierRepository;
import com.umaarov.logistics.delivery.persistence.DeliveryRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiTestBase {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CourierRepository courierRepository;

    @Autowired
    protected DeliveryRepository deliveryRepository;

    @Autowired
    protected OutboxRepository outboxRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        deliveryRepository.deleteAll();
        courierRepository.deleteAll();
    }

    protected String uniquePhone() {
        return "+99890" + String.format("%07d", SEQUENCE.incrementAndGet());
    }

    protected String newTrackingNumber() {
        return "LG" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected JsonNode createCourier(String name, int capacity) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/delivery/couriers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("name", name, "phone", uniquePhone(), "maxActiveDeliveries", capacity))));
        result.andExpect(status().isCreated());
        return json(result);
    }

    protected ResultActions createDelivery(UUID orderId) throws Exception {
        return mockMvc.perform(post("/internal/deliveries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "orderId", orderId,
                        "trackingNumber", newTrackingNumber(),
                        "recipientName", "Aziz Karimov",
                        "recipientPhone", "+998901234567",
                        "city", "Tashkent",
                        "addressLine", "Amir Temur 1, apt 5"))));
    }

    protected JsonNode createDeliveryOk(UUID orderId) throws Exception {
        ResultActions result = createDelivery(orderId);
        result.andExpect(status().isCreated());
        return json(result);
    }

    protected ResultActions updateStatus(String deliveryId, String status, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        if (note != null) {
            body.put("note", note);
        }
        return mockMvc.perform(post("/api/delivery/deliveries/" + deliveryId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected void advance(String deliveryId, String... statuses) throws Exception {
        for (String next : statuses) {
            updateStatus(deliveryId, next, null).andExpect(status().isOk());
        }
    }
}
