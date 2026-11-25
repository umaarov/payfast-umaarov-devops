package com.umaarov.logistics.order.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("order.sweeper")
public record SweeperProperties(boolean enabled, long intervalMs, Duration pendingTimeout) {
}
