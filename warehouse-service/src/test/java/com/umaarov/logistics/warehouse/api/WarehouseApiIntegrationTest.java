package com.umaarov.logistics.warehouse.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
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
class WarehouseApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String newSku() {
        return "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private ResultActions createProduct(String sku, int quantity) throws Exception {
        return mockMvc.perform(post("/api/warehouse/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("sku", sku, "name", "Box " + sku, "initialQuantity", quantity))));
    }

    private ResultActions reserve(UUID orderId, Map<String, Integer> quantitiesBySku) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        quantitiesBySku.forEach((sku, quantity) -> items.add(Map.of("sku", sku, "quantity", quantity)));
        return mockMvc.perform(post("/internal/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("orderId", orderId, "items", items))));
    }

    private JsonNode product(String sku) throws Exception {
        String body = mockMvc.perform(get("/api/warehouse/products/" + sku))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void createdProductStartsWithNothingReserved() throws Exception {
        String sku = newSku();

        createProduct(sku, 10)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/warehouse/products/" + sku))
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.quantityOnHand").value(10))
                .andExpect(jsonPath("$.quantityReserved").value(0))
                .andExpect(jsonPath("$.available").value(10));
    }

    @Test
    void duplicateSkuIsRejectedWithConflict() throws Exception {
        String sku = newSku();
        createProduct(sku, 1).andExpect(status().isCreated());

        createProduct(sku, 1).andExpect(status().isConflict());
    }

    @Test
    void invalidProductIsRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/warehouse/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"bad sku\",\"name\":\"\",\"initialQuantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        mockMvc.perform(get("/api/warehouse/products/" + newSku())).andExpect(status().isNotFound());
    }

    @Test
    void invalidPageSizeIsRejected() throws Exception {
        mockMvc.perform(get("/api/warehouse/products").param("size", "500")).andExpect(status().isBadRequest());
    }

    @Test
    void listContainsCreatedProducts() throws Exception {
        String sku = newSku();
        createProduct(sku, 3).andExpect(status().isCreated());

        mockMvc.perform(get("/api/warehouse/products").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku=='" + sku + "')].quantityOnHand").value(3));
    }

    @Test
    void receiptIncreasesStockOnHand() throws Exception {
        String sku = newSku();
        createProduct(sku, 5).andExpect(status().isCreated());

        mockMvc.perform(post("/api/warehouse/products/" + sku + "/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(12))
                .andExpect(jsonPath("$.available").value(12));
    }

    @Test
    void receiptWithNonPositiveQuantityIsRejected() throws Exception {
        String sku = newSku();
        createProduct(sku, 5).andExpect(status().isCreated());

        mockMvc.perform(post("/api/warehouse/products/" + sku + "/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservationReducesAvailableStock() throws Exception {
        String sku = newSku();
        createProduct(sku, 10).andExpect(status().isCreated());
        UUID orderId = UUID.randomUUID();

        reserve(orderId, Map.of(sku, 4))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.lines[0].sku").value(sku))
                .andExpect(jsonPath("$.lines[0].quantity").value(4));

        JsonNode product = product(sku);
        assertThat(product.get("quantityOnHand").asInt()).isEqualTo(10);
        assertThat(product.get("quantityReserved").asInt()).isEqualTo(4);
        assertThat(product.get("available").asInt()).isEqualTo(6);
    }

    @Test
    void repeatedReservationForSameOrderIsIdempotent() throws Exception {
        String sku = newSku();
        createProduct(sku, 10).andExpect(status().isCreated());
        UUID orderId = UUID.randomUUID();

        String first = reserve(orderId, Map.of(sku, 4)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = reserve(orderId, Map.of(sku, 4)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("id")).isEqualTo(objectMapper.readTree(first).get("id"));
        assertThat(product(sku).get("quantityReserved").asInt()).isEqualTo(4);
    }

    @Test
    void duplicateSkusInOneRequestAreMerged() throws Exception {
        String sku = newSku();
        createProduct(sku, 10).andExpect(status().isCreated());

        mockMvc.perform(post("/internal/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", UUID.randomUUID(),
                                "items", List.of(
                                        Map.of("sku", sku, "quantity", 2),
                                        Map.of("sku", sku, "quantity", 3))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(5));

        assertThat(product(sku).get("quantityReserved").asInt()).isEqualTo(5);
    }

    @Test
    void insufficientStockIsConflictAndReservesNothing() throws Exception {
        String plentiful = newSku();
        String scarce = newSku();
        createProduct(plentiful, 10).andExpect(status().isCreated());
        createProduct(scarce, 1).andExpect(status().isCreated());
        UUID orderId = UUID.randomUUID();

        reserve(orderId, Map.of(plentiful, 5, scarce, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.sku").value(scarce))
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.available").value(1));

        assertThat(product(plentiful).get("quantityReserved").asInt()).isZero();
        assertThat(product(scarce).get("quantityReserved").asInt()).isZero();
        mockMvc.perform(get("/internal/reservations/" + orderId)).andExpect(status().isNotFound());
    }

    @Test
    void unknownSkuInReservationIsNotFoundAndReservesNothing() throws Exception {
        String known = newSku();
        createProduct(known, 10).andExpect(status().isCreated());

        reserve(UUID.randomUUID(), Map.of(known, 1, newSku(), 1)).andExpect(status().isNotFound());

        assertThat(product(known).get("quantityReserved").asInt()).isZero();
    }

    @Test
    void invalidReservationRequestIsRejected() throws Exception {
        mockMvc.perform(post("/internal/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":null,\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void releaseRestoresAvailabilityAndIsIdempotent() throws Exception {
        String sku = newSku();
        createProduct(sku, 10).andExpect(status().isCreated());
        UUID orderId = UUID.randomUUID();
        reserve(orderId, Map.of(sku, 6)).andExpect(status().isCreated());

        mockMvc.perform(post("/internal/reservations/" + orderId + "/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        mockMvc.perform(post("/internal/reservations/" + orderId + "/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        JsonNode product = product(sku);
        assertThat(product.get("quantityReserved").asInt()).isZero();
        assertThat(product.get("available").asInt()).isEqualTo(10);
    }

    @Test
    void commitRemovesStockFromWarehouseAndIsIdempotent() throws Exception {
        String sku = newSku();
        createProduct(sku, 10).andExpect(status().isCreated());
        UUID orderId = UUID.randomUUID();
        reserve(orderId, Map.of(sku, 6)).andExpect(status().isCreated());

        mockMvc.perform(post("/internal/reservations/" + orderId + "/commit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"));
        mockMvc.perform(post("/internal/reservations/" + orderId + "/commit"))
                .andExpect(status().isOk());

        JsonNode product = product(sku);
        assertThat(product.get("quantityOnHand").asInt()).isEqualTo(4);
        assertThat(product.get("quantityReserved").asInt()).isZero();
        assertThat(product.get("available").asInt()).isEqualTo(4);
    }

    @Test
    void committedReservationCannotBeReleased() throws Exception {
        String sku = newSku();
        createProduct(sku, 10).andExpect(status().isCreated());
        UUID orderId = UUID.randomUUID();
        reserve(orderId, Map.of(sku, 2)).andExpect(status().isCreated());
        mockMvc.perform(post("/internal/reservations/" + orderId + "/commit")).andExpect(status().isOk());

        mockMvc.perform(post("/internal/reservations/" + orderId + "/release")).andExpect(status().isConflict());

        assertThat(product(sku).get("quantityOnHand").asInt()).isEqualTo(8);
    }

    @Test
    void releasedReservationCannotBeCommitted() throws Exception {
        String sku = newSku();
        createProduct(sku, 10).andExpect(status().isCreated());
        UUID orderId = UUID.randomUUID();
        reserve(orderId, Map.of(sku, 2)).andExpect(status().isCreated());
        mockMvc.perform(post("/internal/reservations/" + orderId + "/release")).andExpect(status().isOk());

        mockMvc.perform(post("/internal/reservations/" + orderId + "/commit")).andExpect(status().isConflict());

        assertThat(product(sku).get("quantityOnHand").asInt()).isEqualTo(10);
    }

    @Test
    void unknownReservationIsNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get("/internal/reservations/" + orderId)).andExpect(status().isNotFound());
        mockMvc.perform(post("/internal/reservations/" + orderId + "/release")).andExpect(status().isNotFound());
        mockMvc.perform(post("/internal/reservations/" + orderId + "/commit")).andExpect(status().isNotFound());
    }

    @Test
    void concurrentReservationsNeverOversellStock() throws Exception {
        String sku = newSku();
        int stock = 5;
        int attempts = 12;
        createProduct(sku, stock).andExpect(status().isCreated());

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Callable<Integer> task = () -> {
                start.await();
                return reserve(UUID.randomUUID(), Map.of(sku, 1)).andReturn().getResponse().getStatus();
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

        assertThat(created).isEqualTo(stock);
        assertThat(conflicts).isEqualTo(attempts - stock);
        JsonNode product = product(sku);
        assertThat(product.get("quantityReserved").asInt()).isEqualTo(stock);
        assertThat(product.get("available").asInt()).isZero();
    }
}
