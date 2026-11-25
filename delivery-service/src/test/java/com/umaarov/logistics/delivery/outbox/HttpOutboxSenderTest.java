package com.umaarov.logistics.delivery.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.umaarov.logistics.delivery.config.DownstreamProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpOutboxSenderTest {

    private MockRestServiceServer server;
    private HttpOutboxSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DownstreamProperties properties = new DownstreamProperties(
                "http://tracking.test", "http://order.test", Duration.ofSeconds(1), Duration.ofSeconds(1));
        sender = new HttpOutboxSender(builder, properties);
    }

    private OutboxEvent event(OutboxTarget target, String path) {
        return new OutboxEvent(target, path, "{\"a\":1}", Instant.parse("2026-11-25T08:00:00Z"));
    }

    @Test
    void trackingEventIsPostedAsJsonToTheTrackingService() {
        server.expect(requestTo("http://tracking.test/internal/tracking-events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string("{\"a\":1}"))
                .andRespond(withStatus(HttpStatus.CREATED));

        SendResult result = sender.send(event(OutboxTarget.TRACKING, "/internal/tracking-events"));

        assertThat(result.outcome()).isEqualTo(SendOutcome.DELIVERED);
        server.verify();
    }

    @Test
    void orderEventIsPostedToTheOrderService() {
        server.expect(requestTo("http://order.test/internal/orders/42/delivery-events"))
                .andRespond(withSuccess());

        SendResult result = sender.send(event(OutboxTarget.ORDER, "/internal/orders/42/delivery-events"));

        assertThat(result.outcome()).isEqualTo(SendOutcome.DELIVERED);
        server.verify();
    }

    @Test
    void serverErrorsAreRetried() {
        server.expect(requestTo("http://tracking.test/x")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        SendResult result = sender.send(event(OutboxTarget.TRACKING, "/x"));

        assertThat(result.outcome()).isEqualTo(SendOutcome.RETRY);
        assertThat(result.detail()).isEqualTo("HTTP 503");
    }

    @Test
    void tooManyRequestsAndTimeoutsAreRetried() {
        server.expect(requestTo("http://tracking.test/x")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("http://tracking.test/x")).andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT));

        assertThat(sender.send(event(OutboxTarget.TRACKING, "/x")).outcome()).isEqualTo(SendOutcome.RETRY);
        assertThat(sender.send(event(OutboxTarget.TRACKING, "/x")).outcome()).isEqualTo(SendOutcome.RETRY);
    }

    @Test
    void clientErrorsAreRejectedPermanently() {
        server.expect(requestTo("http://tracking.test/x")).andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo("http://tracking.test/x")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://tracking.test/x")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(sender.send(event(OutboxTarget.TRACKING, "/x")).outcome()).isEqualTo(SendOutcome.REJECTED);
        assertThat(sender.send(event(OutboxTarget.TRACKING, "/x")).outcome()).isEqualTo(SendOutcome.REJECTED);
        assertThat(sender.send(event(OutboxTarget.TRACKING, "/x")).outcome()).isEqualTo(SendOutcome.REJECTED);
    }

    @Test
    void connectionFailuresAreRetried() {
        server.expect(requestTo("http://tracking.test/x")).andRespond(request -> {
            throw new IOException("connection refused");
        });

        SendResult result = sender.send(event(OutboxTarget.TRACKING, "/x"));

        assertThat(result.outcome()).isEqualTo(SendOutcome.RETRY);
        assertThat(result.detail()).contains("connection refused");
    }
}
