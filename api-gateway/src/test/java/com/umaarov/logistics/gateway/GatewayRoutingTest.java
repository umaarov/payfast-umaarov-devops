package com.umaarov.logistics.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingTest {

    private static final String KEY = "test-key-aaaaaaaaaaaaaaaa";
    private static final String OTHER_KEY = "test-key-bbbbbbbbbbbbbbbb";

    private static final FakeDownstream DOWNSTREAM = new FakeDownstream();

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("ORDER_SERVICE_URL", DOWNSTREAM::baseUrl);
        registry.add("WAREHOUSE_SERVICE_URL", DOWNSTREAM::baseUrl);
        registry.add("DELIVERY_SERVICE_URL", DOWNSTREAM::baseUrl);
        registry.add("TRACKING_SERVICE_URL", DOWNSTREAM::baseUrl);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void clearRecordedRequests() {
        DOWNSTREAM.clear();
    }

    @Test
    void healthIsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().returnResult();
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        client.get().uri("/actuator/info").header("X-API-Key", KEY).exchange().expectStatus().isNotFound()
                .expectBody().returnResult();
        client.get().uri("/actuator/env").header("X-API-Key", KEY).exchange().expectStatus().isNotFound()
                .expectBody().returnResult();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/orders", "/api/orders/42", "/api/warehouse/products", "/api/delivery/couriers", "/api/tracking"
    })
    void protectedRoutesRejectMissingKey(String path) {
        client.get().uri(path).exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("WWW-Authenticate")
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(401);

        assertThat(DOWNSTREAM.requests()).isEmpty();
    }

    @Test
    void wrongKeysAreRejected() {
        String[] wrongKeys = {"nope", "", "test-key-aaaaaaaaaaaaaaa", "test-key-aaaaaaaaaaaaaaaaX", KEY.toUpperCase()};
        for (String wrong : wrongKeys) {
            client.get().uri("/api/orders").header("X-API-Key", wrong).exchange().expectStatus().isUnauthorized()
                .expectBody().returnResult();
        }

        assertThat(DOWNSTREAM.requests()).isEmpty();
    }

    @Test
    void everyConfiguredKeyIsAccepted() {
        client.get().uri("/api/orders").header("X-API-Key", KEY).exchange().expectStatus().isOk()
                .expectBody().returnResult();
        client.get().uri("/api/orders").header("X-API-Key", OTHER_KEY).exchange().expectStatus().isOk()
                .expectBody().returnResult();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/orders", "/api/orders/42", "/api/warehouse/products", "/api/delivery/couriers",
            "/api/tracking/LGK4M9X2PQ7A"})
    void validKeyIsForwardedWithTheSamePath(String path) {
        client.get().uri(path).header("X-API-Key", KEY).exchange().expectStatus().isOk()
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).singleElement()
                .satisfies(request -> assertThat(request.uri()).isEqualTo(path));
    }

    @Test
    void apiKeyIsNeverForwardedDownstream() {
        client.get().uri("/api/orders").header("X-API-Key", KEY).exchange().expectStatus().isOk()
                .expectBody().returnResult();
        client.get().uri("/api/tracking/LGK4M9X2PQ7A").header("X-API-Key", KEY).exchange().expectStatus().isOk()
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).hasSize(2)
                .allSatisfy(request -> assertThat(request.header("X-API-Key")).isNull());
    }

    @Test
    void trackingTimelineIsPublic() {
        client.get().uri("/api/tracking/LGK4M9X2PQ7A").exchange().expectStatus().isOk()
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).singleElement()
                .satisfies(request -> assertThat(request.uri()).isEqualTo("/api/tracking/LGK4M9X2PQ7A"));
    }

    @Test
    void onlyTheTimelineReadIsPublicUnderTracking() {
        client.post().uri("/api/tracking/LGK4M9X2PQ7A").exchange().expectStatus().isUnauthorized()
                .expectBody().returnResult();
        client.get().uri("/api/tracking/LGK4M9X2PQ7A/extra").exchange().expectStatus().isUnauthorized()
                .expectBody().returnResult();
        client.get().uri("/api/tracking/LGK4M9X2PQ7A/").exchange().expectStatus().isUnauthorized()
                .expectBody().returnResult();
        client.head().uri("/api/tracking/LGK4M9X2PQ7A").exchange().expectStatus().isUnauthorized()
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).isEmpty();
    }

    @Test
    void unknownPathsAreNotFoundForCallersWithAKeyAndUnauthorizedOtherwise() {
        client.get().uri("/api/unknown").header("X-API-Key", KEY).exchange().expectStatus().isNotFound()
                .expectBody().returnResult();
        client.get().uri("/api/unknown").exchange().expectStatus().isUnauthorized()
                .expectBody().returnResult();
        client.get().uri("/").header("X-API-Key", KEY).exchange().expectStatus().isNotFound()
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).isEmpty();
    }

    @Test
    void methodBodyAndQueryAreForwarded() {
        client.post().uri("/api/orders?source=web")
                .header("X-API-Key", KEY)
                .header("Idempotency-Key", "abc-123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"city\":\"Tashkent\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri()).isEqualTo("/api/orders?source=web");
            assertThat(request.body()).isEqualTo("{\"city\":\"Tashkent\"}");
            assertThat(request.header("Idempotency-Key")).isEqualTo("abc-123");
            assertThat(request.header(HttpHeaders.CONTENT_TYPE)).startsWith("application/json");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/reservations", "/internal/orders/1/delivery-events", "/internal", "/Internal/deliveries",
            "/INTERNAL/tracking-events"
    })
    void internalPathsAreNeverReachableEvenWithAKey(String path) {
        client.get().uri(path).header("X-API-Key", KEY).exchange().expectStatus().isNotFound()
                .expectBody().returnResult();
        client.post().uri(path).header("X-API-Key", KEY).exchange().expectStatus().isNotFound()
                .expectBody().returnResult();
        client.get().uri(path).exchange().expectStatus().isNotFound()
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/orders/../../internal/orders/1/delivery-events",
            "/api/orders/../internal/reservations",
            "/api/orders/%2e%2e/%2e%2e/internal/reservations",
            "/api/orders/%2E%2E/internal/reservations",
            "/api/orders/..%2finternal/reservations",
            "/api/orders/%252e%252e/internal/reservations",
            "/api/orders/./42",
            "/api/orders/42%5c..%5cinternal",
            "/api/orders/%00"
    })
    void pathTricksAreRejectedBeforeAnythingIsForwarded(String path) {
        client.get().uri(path).header("X-API-Key", KEY).exchange()
                .expectStatus().value(status -> assertThat(status).isIn(400, 404))
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/orders//42", "//internal/reservations", "/api//orders", "///"})
    void emptyPathSegmentsAreRejectedBeforeAnythingIsForwarded(String target) throws Exception {
        String statusLine = rawRequestStatusLine(target);

        assertThat(statusLine).containsAnyOf(" 400 ", " 404 ");
        assertThat(DOWNSTREAM.requests()).isEmpty();
    }

    private String rawRequestStatusLine(String target) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            String request = "GET " + target + " HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "X-API-Key: " + KEY + "\r\n"
                    + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            return reader.readLine();
        }
    }

    @Test
    void rejectedPathTricksAnswerBadRequest() {
        client.get().uri("/api/orders/../internal/reservations").header("X-API-Key", KEY).exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().returnResult();
    }

    @Test
    void requestIdIsGeneratedAndForwarded() {
        String responseId = client.get().uri("/api/orders").header("X-API-Key", KEY).exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .returnResult(String.class).getResponseHeaders().getFirst("X-Request-Id");

        assertThat(DOWNSTREAM.requests()).singleElement()
                .satisfies(request -> assertThat(request.header("X-Request-Id")).isEqualTo(responseId));
        assertThat(responseId).matches("[0-9a-f-]{36}");
    }

    @Test
    void wellFormedRequestIdFromTheCallerIsKept() {
        client.get().uri("/api/orders").header("X-API-Key", KEY).header("X-Request-Id", "client-req-0001")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "client-req-0001")
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).singleElement()
                .satisfies(request -> assertThat(request.header("X-Request-Id")).isEqualTo("client-req-0001"));
    }

    @Test
    void malformedRequestIdIsReplaced() {
        client.get().uri("/api/orders").header("X-API-Key", KEY).header("X-Request-Id", "x y")
                .exchange().expectStatus().isOk()
                .expectHeader().value("X-Request-Id", value -> assertThat(value).matches("[0-9a-f-]{36}"))
                .expectBody().returnResult();
    }

    @Test
    void rejectedRequestsStillCarryARequestId() {
        client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id")
                .expectBody().returnResult();
    }

    @Test
    void routedResponsesCarrySecurityHeaders() {
        client.get().uri("/api/orders").header("X-API-Key", KEY).exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().exists("X-Frame-Options")
                .expectBody().returnResult();
    }

    @Test
    void oversizedBodiesAreRefused() {
        byte[] body = new byte[2 * 1024 * 1024];

        client.post().uri("/api/orders").header("X-API-Key", KEY)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(413)
                .expectBody().returnResult();

        assertThat(DOWNSTREAM.requests()).isEmpty();
    }

    @Test
    void smallBodiesPassTheSizeLimit() {
        client.post().uri("/api/orders").header("X-API-Key", KEY)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(new byte[1024])
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult();
    }
}
