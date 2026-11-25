package com.umaarov.logistics.order.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("order.downstream")
public record DownstreamProperties(
        String warehouseUrl,
        String deliveryUrl,
        String trackingUrl,
        Duration connectTimeout,
        Duration readTimeout) {
}
