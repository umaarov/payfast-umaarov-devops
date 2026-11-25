package com.umaarov.logistics.delivery.outbox;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxRepository outbox;
    private final OutboxSender sender;
    private final Clock clock;

    public OutboxDispatcher(OutboxRepository outbox, OutboxSender sender, Clock clock) {
        this.outbox = outbox;
        this.sender = sender;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${delivery.outbox.interval-ms:2000}")
    public void dispatchPending() {
        for (OutboxTarget target : OutboxTarget.values()) {
            dispatch(target);
        }
    }

    private void dispatch(OutboxTarget target) {
        List<OutboxEvent> batch = outbox.findTop50ByTargetAndStateOrderByIdAsc(target, OutboxState.PENDING);
        for (OutboxEvent event : batch) {
            SendResult result = sender.send(event);
            switch (result.outcome()) {
                case DELIVERED -> event.markSent(clock.instant());
                case REJECTED -> {
                    event.markRejected(result.detail(), clock.instant());
                    log.warn("Outbox event {} for {} was rejected: {}", event.getId(), target, result.detail());
                }
                case RETRY -> {
                    event.recordFailedAttempt(result.detail(), clock.instant());
                    outbox.save(event);
                    log.warn("Outbox event {} for {} will be retried: {}", event.getId(), target, result.detail());
                    return;
                }
            }
            outbox.save(event);
        }
    }
}
