package com.umaarov.logistics.order.client;

import com.umaarov.logistics.order.config.DownstreamProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TrackingHttpClient implements TrackingClient {

    private static final String SERVICE = "tracking-service";

    private final RestClient client;

    public TrackingHttpClient(RestClient.Builder builder, DownstreamProperties properties) {
        this.client = builder.clone().baseUrl(properties.trackingUrl()).build();
    }

    @Override
    public void record(TrackingEventRequest request) {
        try {
            client.post()
                    .uri("/internal/tracking-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw DownstreamErrors.classify(SERVICE, exception);
        }
    }
}
