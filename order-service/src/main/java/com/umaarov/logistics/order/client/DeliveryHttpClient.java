package com.umaarov.logistics.order.client;

import com.umaarov.logistics.order.config.DownstreamProperties;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DeliveryHttpClient implements DeliveryClient {

    private static final String SERVICE = "delivery-service";

    private final RestClient client;

    public DeliveryHttpClient(RestClient.Builder builder, DownstreamProperties properties) {
        this.client = builder.clone().baseUrl(properties.deliveryUrl()).build();
    }

    @Override
    public void create(DeliveryRequest request) {
        try {
            client.post()
                    .uri("/internal/deliveries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new NoCourierAvailableException();
            }
            throw DownstreamErrors.classify(SERVICE, exception);
        } catch (RestClientException exception) {
            throw DownstreamErrors.classify(SERVICE, exception);
        }
    }

    @Override
    public CancelResult cancel(UUID orderId) {
        try {
            client.post().uri("/internal/deliveries/by-order/{orderId}/cancel", orderId)
                    .retrieve()
                    .toBodilessEntity();
            return CancelResult.CANCELLED;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 404) {
                return CancelResult.NOT_FOUND;
            }
            if (status == 409) {
                throw new DeliveryNotCancellableException();
            }
            throw DownstreamErrors.classify(SERVICE, exception);
        } catch (RestClientException exception) {
            throw DownstreamErrors.classify(SERVICE, exception);
        }
    }
}
