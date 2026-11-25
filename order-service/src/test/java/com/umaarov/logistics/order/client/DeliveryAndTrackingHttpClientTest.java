package com.umaarov.logistics.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.umaarov.logistics.order.config.DownstreamProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeliveryAndTrackingHttpClientTest {

    private final UUID orderId = UUID.fromString("0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55");
    private MockRestServiceServer server;
    private DeliveryHttpClient deliveryClient;
    private TrackingHttpClient trackingClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DownstreamProperties properties = new DownstreamProperties(
                "http://warehouse.test", "http://delivery.test", "http://tracking.test",
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        deliveryClient = new DeliveryHttpClient(builder, properties);
        trackingClient = new TrackingHttpClient(builder, properties);
    }

    private DeliveryRequest deliveryRequest() {
        return new DeliveryRequest(orderId, "LGK4M9X2PQ7A", "Aziz", "+998901234567", "Tashkent", "Street 1");
    }

    @Test
    void deliveryIsCreatedWithAllRecipientFields() {
        server.expect(requestTo("http://delivery.test/internal/deliveries"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.trackingNumber").value("LGK4M9X2PQ7A"))
                .andExpect(jsonPath("$.recipientName").value("Aziz"))
                .andExpect(jsonPath("$.city").value("Tashkent"))
                .andExpect(jsonPath("$.addressLine").value("Street 1"))
                .andRespond(withStatus(HttpStatus.CREATED));

        deliveryClient.create(deliveryRequest());

        server.verify();
    }

    @Test
    void conflictOnCreateMeansNoCourier() {
        server.expect(requestTo("http://delivery.test/internal/deliveries"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> deliveryClient.create(deliveryRequest()))
                .isInstanceOf(NoCourierAvailableException.class);
    }

    @Test
    void createFailuresAreClassified() {
        server.expect(requestTo("http://delivery.test/internal/deliveries"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://delivery.test/internal/deliveries"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo("http://delivery.test/internal/deliveries")).andRespond(request -> {
            throw new IOException("timed out");
        });

        assertThatThrownBy(() -> deliveryClient.create(deliveryRequest()))
                .isInstanceOf(DownstreamUnavailableException.class);
        assertThatThrownBy(() -> deliveryClient.create(deliveryRequest()))
                .isInstanceOf(DownstreamRejectedException.class);
        assertThatThrownBy(() -> deliveryClient.create(deliveryRequest()))
                .isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    void cancelReportsWhatHappened() {
        String url = "http://delivery.test/internal/deliveries/by-order/" + orderId + "/cancel";
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.OK));
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(deliveryClient.cancel(orderId)).isEqualTo(CancelResult.CANCELLED);
        assertThat(deliveryClient.cancel(orderId)).isEqualTo(CancelResult.NOT_FOUND);
    }

    @Test
    void cancelOfPickedUpDeliveryIsNotAllowed() {
        server.expect(requestTo("http://delivery.test/internal/deliveries/by-order/" + orderId + "/cancel"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> deliveryClient.cancel(orderId))
                .isInstanceOf(DeliveryNotCancellableException.class);
    }

    @Test
    void cancelOutageIsUnavailable() {
        server.expect(requestTo("http://delivery.test/internal/deliveries/by-order/" + orderId + "/cancel"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> deliveryClient.cancel(orderId)).isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    void trackingEventIsPostedWithItsId() {
        UUID eventId = UUID.randomUUID();
        server.expect(requestTo("http://tracking.test/internal/tracking-events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.location").value("Tashkent"))
                .andRespond(withStatus(HttpStatus.CREATED));

        trackingClient.record(new TrackingEventRequest(eventId, "LGK4M9X2PQ7A", "CREATED", "Order accepted", "Tashkent"));

        server.verify();
    }

    @Test
    void trackingFailuresAreClassified() {
        TrackingEventRequest request =
                new TrackingEventRequest(UUID.randomUUID(), "LGK4M9X2PQ7A", "CREATED", "Order accepted", "Tashkent");
        server.expect(requestTo("http://tracking.test/internal/tracking-events"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://tracking.test/internal/tracking-events"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> trackingClient.record(request)).isInstanceOf(DownstreamUnavailableException.class);
        assertThatThrownBy(() -> trackingClient.record(request)).isInstanceOf(DownstreamRejectedException.class);
    }
}
