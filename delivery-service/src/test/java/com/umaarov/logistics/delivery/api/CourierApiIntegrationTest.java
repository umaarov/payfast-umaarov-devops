package com.umaarov.logistics.delivery.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CourierApiIntegrationTest extends ApiTestBase {

    @Test
    void courierGetsDefaultCapacityAndStartsAvailable() throws Exception {
        mockMvc.perform(post("/api/delivery/couriers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Bekzod", "phone", uniquePhone()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.maxActiveDeliveries").value(5))
                .andExpect(jsonPath("$.activeDeliveries").value(0));
    }

    @Test
    void invalidCourierIsRejected() throws Exception {
        mockMvc.perform(post("/api/delivery/couriers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phone\":\"abc\",\"maxActiveDeliveries\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void duplicatePhoneIsConflict() throws Exception {
        String phone = uniquePhone();
        String body = objectMapper.writeValueAsString(Map.of("name", "Bekzod", "phone", phone));
        mockMvc.perform(post("/api/delivery/couriers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/delivery/couriers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void availabilityCanBeToggled() throws Exception {
        String id = createCourier("Bekzod", 3).get("id").asText();

        mockMvc.perform(post("/api/delivery/couriers/" + id + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFF_DUTY"));
        mockMvc.perform(post("/api/delivery/couriers/" + id + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":true}"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void availabilityRequiresAValue() throws Exception {
        String id = createCourier("Bekzod", 3).get("id").asText();

        mockMvc.perform(post("/api/delivery/couriers/" + id + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCourierIsNotFound() throws Exception {
        mockMvc.perform(get("/api/delivery/couriers/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void listIsSortedByName() throws Exception {
        createCourier("Zafar", 3);
        createCourier("Alisher", 3);

        mockMvc.perform(get("/api/delivery/couriers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alisher"))
                .andExpect(jsonPath("$[1].name").value("Zafar"));
    }
}
