package com.umaarov.logistics.delivery.outbox;

import com.umaarov.logistics.delivery.config.DownstreamProperties;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpOutboxSender implements OutboxSender {

    private final Map<OutboxTarget, RestClient> clients = new EnumMap<>(OutboxTarget.class);

    public HttpOutboxSender(RestClient.Builder builder, DownstreamProperties properties) {
        clients.put(OutboxTarget.TRACKING, builder.clone().baseUrl(properties.trackingUrl()).build());
        clients.put(OutboxTarget.ORDER, builder.clone().baseUrl(properties.orderUrl()).build());
    }

    @Override
    public SendResult send(OutboxEvent event) {
        try {
            clients.get(event.getTarget()).post()
                    .uri(event.getPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            return SendResult.delivered();
        } catch (RestClientResponseException exception) {
            String detail = "HTTP " + exception.getStatusCode().value();
            return isTransient(exception.getStatusCode()) ? SendResult.retry(detail) : SendResult.rejected(detail);
        } catch (RestClientException exception) {
            return SendResult.retry(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private boolean isTransient(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 408 || status.value() == 429;
    }
}
