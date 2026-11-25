package com.umaarov.logistics.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DeliveryServiceApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReportsUp() {
        var response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void livenessAndReadinessProbesReportUp() {
        var liveness = restTemplate.getForEntity("/actuator/health/liveness", String.class);
        var readiness = restTemplate.getForEntity("/actuator/health/readiness", String.class);

        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getBody()).contains("UP");
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).contains("UP");
    }
}
