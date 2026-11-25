package com.umaarov.logistics.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayDownstreamFailureTest {

    @DynamicPropertySource
    static void deadDownstreams(DynamicPropertyRegistry registry) {
        registry.add("ORDER_SERVICE_URL", () -> "http://127.0.0.1:1");
        registry.add("WAREHOUSE_SERVICE_URL", () -> "http://127.0.0.1:1");
        registry.add("DELIVERY_SERVICE_URL", () -> "http://127.0.0.1:1");
        registry.add("TRACKING_SERVICE_URL", () -> "http://127.0.0.1:1");
    }

    @Autowired
    private WebTestClient client;

    @Test
    void unreachableServiceAnswersServiceUnavailableInsteadOfHanging() {
        client.get().uri("/api/orders").header("X-API-Key", "test-key-aaaaaaaaaaaaaaaa").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().returnResult();
    }

    @Test
    void gatewayStaysHealthyWhenServicesAreDown() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().returnResult();
    }
}
