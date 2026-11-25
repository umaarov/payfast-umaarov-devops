package com.umaarov.logistics.order.domain;

import com.umaarov.logistics.order.config.SweeperProperties;
import com.umaarov.logistics.order.persistence.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrderSweeper.class);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final SweeperProperties properties;
    private final Clock clock;

    public OrderSweeper(
            OrderRepository orders, OrderService orderService, SweeperProperties properties, Clock clock) {
        this.orders = orders;
        this.orderService = orderService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${order.sweeper.interval-ms:30000}")
    public void sweep() {
        rejectStalePendingOrders();
        retryPendingCompensations();
    }

    public void rejectStalePendingOrders() {
        Instant cutoff = clock.instant().minus(properties.pendingTimeout());
        for (Order order : orders.findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus.PENDING, cutoff)) {
            try {
                orderService.rejectStale(order.getId());
            } catch (OptimisticLockingFailureException exception) {
                log.info("Order {} changed while it was being swept; skipped", order.getId());
            }
        }
    }

    public void retryPendingCompensations() {
        for (Order order : orders.findTop50ByCompensationPendingTrueOrderByCreatedAtAsc()) {
            try {
                orderService.retryCompensation(order.getId());
            } catch (OptimisticLockingFailureException exception) {
                log.info("Order {} changed while it was being swept; skipped", order.getId());
            }
        }
    }
}
