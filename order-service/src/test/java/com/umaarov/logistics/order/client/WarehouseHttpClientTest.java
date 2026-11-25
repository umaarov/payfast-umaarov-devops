package com.umaarov.logistics.order.client;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umaarov.logistics.order.config.DownstreamProperties;
import com.umaarov.logistics.order.domain.OrderLine;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WarehouseHttpClientTest {

    private final UUID orderId = UUID.fromString("0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55");
    private MockRestServiceServer server;
    private WarehouseHttpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DownstreamProperties properties = new DownstreamProperties(
                "http://warehouse.test", "http://delivery.test", "http://tracking.test",
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        client = new WarehouseHttpClient(builder, properties, new ObjectMapper());
    }

    @Test
    void reservePostsOrderIdAndItems() {
        server.expect(requestTo("http://warehouse.test/internal/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.items[0].sku").value("BOX-M"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andRespond(withStatus(HttpStatus.CREATED));

        client.reserve(orderId, List.of(new OrderLine("BOX-M", 2)));

        server.verify();
    }

    @Test
    void insufficientStockIsReadFromTheProblemBody() {
        server.expect(requestTo("http://warehouse.test/internal/reservations"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"sku\":\"BOX-M\",\"requested\":5,\"available\":1}"));

        assertThatThrownBy(() -> client.reserve(orderId, List.of(new OrderLine("BOX-M", 5))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("BOX-M")
                .hasMessageContaining("requested 5")
                .hasMessageContaining("available 1");
    }

    @Test
    void conflictWithoutStockDetailIsTreatedAsTransient() {
        server.expect(requestTo("http://warehouse.test/internal/reservations"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"detail\":\"The request conflicts with existing data\"}"));

        assertThatThrownBy(() -> client.reserve(orderId, List.of(new OrderLine("BOX-M", 1))))
                .isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    void unknownSkuIsReportedWithTheServiceDetail() {
        server.expect(requestTo("http://warehouse.test/internal/reservations"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"detail\":\"Product with sku NOPE does not exist\"}"));

        assertThatThrownBy(() -> client.reserve(orderId, List.of(new OrderLine("NOPE", 1))))
                .isInstanceOf(UnknownProductException.class)
                .hasMessage("Product with sku NOPE does not exist");
    }

    @Test
    void serverErrorsAndNetworkFailuresAreUnavailable() {
        server.expect(requestTo("http://warehouse.test/internal/reservations"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://warehouse.test/internal/reservations")).andRespond(request -> {
            throw new IOException("connection refused");
        });

        assertThatThrownBy(() -> client.reserve(orderId, List.of(new OrderLine("BOX-M", 1))))
                .isInstanceOf(DownstreamUnavailableException.class);
        assertThatThrownBy(() -> client.reserve(orderId, List.of(new OrderLine("BOX-M", 1))))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    void otherClientErrorsAreRejections() {
        server.expect(requestTo("http://warehouse.test/internal/reservations"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.reserve(orderId, List.of(new OrderLine("BOX-M", 1))))
                .isInstanceOf(DownstreamRejectedException.class);
    }

    @Test
    void releaseOfUnknownReservationIsSuccess() {
        server.expect(requestTo("http://warehouse.test/internal/reservations/" + orderId + "/release"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatCode(() -> client.release(orderId)).doesNotThrowAnyException();
    }

    @Test
    void releaseFailuresAreClassified() {
        server.expect(requestTo("http://warehouse.test/internal/reservations/" + orderId + "/release"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo("http://warehouse.test/internal/reservations/" + orderId + "/release"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.release(orderId)).isInstanceOf(DownstreamUnavailableException.class);
        assertThatThrownBy(() -> client.release(orderId)).isInstanceOf(DownstreamRejectedException.class);
    }

    @Test
    void commitPostsToTheCommitEndpoint() {
        server.expect(requestTo("http://warehouse.test/internal/reservations/" + orderId + "/commit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        client.commit(orderId);

        server.verify();
    }

    @Test
    void commitOfReleasedReservationIsRejected() {
        server.expect(requestTo("http://warehouse.test/internal/reservations/" + orderId + "/commit"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.commit(orderId)).isInstanceOf(DownstreamRejectedException.class);
    }
}
