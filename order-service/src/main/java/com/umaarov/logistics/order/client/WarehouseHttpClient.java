package com.umaarov.logistics.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umaarov.logistics.order.config.DownstreamProperties;
import com.umaarov.logistics.order.domain.OrderLine;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class WarehouseHttpClient implements WarehouseClient {

    private static final String SERVICE = "warehouse-service";

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public WarehouseHttpClient(RestClient.Builder builder, DownstreamProperties properties, ObjectMapper objectMapper) {
        this.client = builder.clone().baseUrl(properties.warehouseUrl()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public void reserve(UUID orderId, List<OrderLine> lines) {
        try {
            client.post()
                    .uri("/internal/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("orderId", orderId, "items", lines))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw translateReserveFailure(exception);
        } catch (RestClientException exception) {
            throw DownstreamErrors.classify(SERVICE, exception);
        }
    }

    @Override
    public void release(UUID orderId) {
        try {
            client.post().uri("/internal/reservations/{orderId}/release", orderId).retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) {
                throw DownstreamErrors.classify(SERVICE, exception);
            }
        } catch (RestClientException exception) {
            throw DownstreamErrors.classify(SERVICE, exception);
        }
    }

    @Override
    public void commit(UUID orderId) {
        try {
            client.post().uri("/internal/reservations/{orderId}/commit", orderId).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw DownstreamErrors.classify(SERVICE, exception);
        }
    }

    private DownstreamException translateReserveFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        JsonNode body = DownstreamErrors.body(objectMapper, exception);
        if (status == 409 && body != null && body.hasNonNull("sku")) {
            return new InsufficientStockException(
                    body.get("sku").asText(), body.path("requested").asInt(), body.path("available").asInt());
        }
        if (status == 409) {
            return new DownstreamUnavailableException(SERVICE + " reported a concurrent conflict");
        }
        if (status == 404) {
            String detail = body != null ? body.path("detail").asText("Unknown product") : "Unknown product";
            return new UnknownProductException(detail);
        }
        return DownstreamErrors.classify(SERVICE, exception);
    }
}
