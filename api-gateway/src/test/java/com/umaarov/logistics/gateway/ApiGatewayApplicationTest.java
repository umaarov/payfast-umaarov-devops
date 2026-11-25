package com.umaarov.logistics.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpointReportsUp() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).contains("UP"));
    }
}
