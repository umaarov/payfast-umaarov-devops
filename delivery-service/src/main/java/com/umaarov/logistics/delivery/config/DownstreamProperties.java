package com.umaarov.logistics.delivery.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("delivery.downstream")
public record DownstreamProperties(
        String trackingUrl,
        String orderUrl,
        Duration connectTimeout,
        Duration readTimeout) {
}
