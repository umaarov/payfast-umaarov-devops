package com.umaarov.logistics.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umaarov.logistics.order.client.CancelResult;
import com.umaarov.logistics.order.client.DeliveryClient;
import com.umaarov.logistics.order.client.TrackingClient;
import com.umaarov.logistics.order.client.WarehouseClient;
import com.umaarov.logistics.order.persistence.OrderRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiTestBase {

    @MockitoBean
    protected WarehouseClient warehouse;

    @MockitoBean
    protected DeliveryClient delivery;

    @MockitoBean
    protected TrackingClient tracking;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected OrderRepository orderRepository;

    @BeforeEach
    void resetState() {
        orderRepository.deleteAll();
        when(delivery.cancel(any())).thenReturn(CancelResult.NOT_FOUND);
    }

    protected Map<String, Object> orderBody(Map<String, Integer> quantitiesBySku) {
        List<Map<String, Object>> items = new ArrayList<>();
        quantitiesBySku.forEach((sku, quantity) -> items.add(Map.of("sku", sku, "quantity", quantity)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerName", "Aziz Karimov");
        body.put("customerPhone", "+998901234567");
        body.put("city", "Tashkent");
        body.put("addressLine", "Amir Temur 1, apt 5");
        body.put("items", items);
        return body;
    }

    protected ResultActions placeOrder(String idempotencyKey, Map<String, Integer> quantitiesBySku) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderBody(quantitiesBySku)));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    protected JsonNode placeConfirmedOrder() throws Exception {
        ResultActions result = placeOrder(null, Map.of("BOX-M", 2));
        result.andExpect(status().isCreated());
        return json(result);
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected ResultActions deliveryEvent(String orderId, String status) throws Exception {
        return mockMvc.perform(post("/internal/orders/" + orderId + "/delivery-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("eventId", UUID.randomUUID(), "status", status))));
    }
}
