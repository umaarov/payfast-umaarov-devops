package com.umaarov.logistics.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class DownstreamErrors {

    private DownstreamErrors() {
    }

    static DownstreamException classify(String service, RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status >= 500 || status == 408 || status == 429) {
                return new DownstreamUnavailableException(service + " responded with HTTP " + status);
            }
            return new DownstreamRejectedException(service + " rejected the request with HTTP " + status);
        }
        return new DownstreamUnavailableException(service + " is unreachable: " + exception.getMessage());
    }

    static JsonNode body(ObjectMapper mapper, RestClientResponseException exception) {
        try {
            return mapper.readTree(exception.getResponseBodyAsString());
        } catch (Exception ignored) {
            return null;
        }
    }
}
