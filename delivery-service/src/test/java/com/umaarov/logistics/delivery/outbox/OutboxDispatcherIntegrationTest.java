package com.umaarov.logistics.delivery.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class OutboxDispatcherIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private OutboxRepository outboxRepository;

    @MockitoBean
    private OutboxSender sender;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    private OutboxEvent queue(OutboxTarget target, String payload) {
        return outboxRepository.save(new OutboxEvent(target, "/internal/test", payload, NOW));
    }

    private OutboxEvent reload(OutboxEvent event) {
        return outboxRepository.findById(event.getId()).orElseThrow();
    }

    @Test
    void deliveredEventsAreMarkedSent() {
        OutboxEvent event = queue(OutboxTarget.TRACKING, "{}");
        when(sender.send(any())).thenReturn(SendResult.delivered());

        dispatcher.dispatchPending();

        OutboxEvent stored = reload(event);
        assertThat(stored.getState()).isEqualTo(OutboxState.SENT);
        assertThat(stored.getAttempts()).isEqualTo(1);
    }

    @Test
    void sentEventsAreNotSentAgain() {
        queue(OutboxTarget.TRACKING, "{}");
        when(sender.send(any())).thenReturn(SendResult.delivered());
        dispatcher.dispatchPending();

        dispatcher.dispatchPending();

        verify(sender, times(1)).send(any());
    }

    @Test
    void retryKeepsEventPendingAndStopsOnlyThatTarget() {
        OutboxEvent first = queue(OutboxTarget.TRACKING, "first");
        OutboxEvent second = queue(OutboxTarget.TRACKING, "second");
        OutboxEvent other = queue(OutboxTarget.ORDER, "other");
        when(sender.send(any())).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            return event.getTarget() == OutboxTarget.TRACKING
                    ? SendResult.retry("HTTP 503")
                    : SendResult.delivered();
        });

        dispatcher.dispatchPending();

        OutboxEvent storedFirst = reload(first);
        assertThat(storedFirst.getState()).isEqualTo(OutboxState.PENDING);
        assertThat(storedFirst.getAttempts()).isEqualTo(1);
        assertThat(storedFirst.getLastError()).isEqualTo("HTTP 503");
        assertThat(reload(second).getAttempts()).isZero();
        assertThat(reload(other).getState()).isEqualTo(OutboxState.SENT);
    }

    @Test
    void eventsOfOneTargetAreSentInCreationOrder() {
        OutboxEvent first = queue(OutboxTarget.TRACKING, "first");
        OutboxEvent second = queue(OutboxTarget.TRACKING, "second");
        List<String> order = new ArrayList<>();
        when(sender.send(any())).thenAnswer(invocation -> {
            order.add(invocation.<OutboxEvent>getArgument(0).getPayload());
            return SendResult.delivered();
        });

        dispatcher.dispatchPending();

        assertThat(order).containsExactly("first", "second");
        assertThat(reload(first).getState()).isEqualTo(OutboxState.SENT);
        assertThat(reload(second).getState()).isEqualTo(OutboxState.SENT);
    }

    @Test
    void rejectedEventIsParkedAndDispatchContinues() {
        OutboxEvent rejected = queue(OutboxTarget.TRACKING, "rejected");
        OutboxEvent next = queue(OutboxTarget.TRACKING, "next");
        when(sender.send(any())).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            return "rejected".equals(event.getPayload())
                    ? SendResult.rejected("HTTP 409")
                    : SendResult.delivered();
        });

        dispatcher.dispatchPending();

        assertThat(reload(rejected).getState()).isEqualTo(OutboxState.REJECTED);
        assertThat(reload(rejected).getLastError()).isEqualTo("HTTP 409");
        assertThat(reload(next).getState()).isEqualTo(OutboxState.SENT);
    }

    @Test
    void rejectedEventsAreNeverRetried() {
        queue(OutboxTarget.TRACKING, "rejected");
        when(sender.send(any())).thenReturn(SendResult.rejected("HTTP 400"));
        dispatcher.dispatchPending();

        dispatcher.dispatchPending();

        verify(sender, times(1)).send(any());
    }

    @Test
    void emptyOutboxSendsNothing() {
        dispatcher.dispatchPending();

        verify(sender, never()).send(any());
    }
}
