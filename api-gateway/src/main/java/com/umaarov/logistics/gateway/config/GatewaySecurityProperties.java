package com.umaarov.logistics.gateway.config;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.security")
public record GatewaySecurityProperties(List<String> apiKeys) {

    public static final int MIN_KEY_LENGTH = 16;

    public GatewaySecurityProperties {
        List<String> cleaned = apiKeys == null
                ? List.of()
                : apiKeys.stream().filter(Objects::nonNull).map(String::trim).filter(key -> !key.isEmpty()).toList();
        if (cleaned.isEmpty()) {
            throw new IllegalStateException("GATEWAY_API_KEYS must contain at least one API key");
        }
        if (cleaned.stream().anyMatch(key -> key.length() < MIN_KEY_LENGTH)) {
            throw new IllegalStateException(
                    "Every API key in GATEWAY_API_KEYS must be at least " + MIN_KEY_LENGTH + " characters long");
        }
        apiKeys = cleaned;
    }

    @Override
    public String toString() {
        return "GatewaySecurityProperties[apiKeys=" + apiKeys.size() + " configured]";
    }
}
