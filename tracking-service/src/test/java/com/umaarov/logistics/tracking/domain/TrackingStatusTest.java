package com.umaarov.logistics.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackingStatusTest {

    @Test
    void onlyDeliveredAndCancelledAreTerminal() {
        List<TrackingStatus> terminal = Arrays.stream(TrackingStatus.values())
                .filter(TrackingStatus::isTerminal)
                .toList();

        assertThat(terminal).containsExactlyInAnyOrder(TrackingStatus.DELIVERED, TrackingStatus.CANCELLED);
    }

    @Test
    void failedDeliveryCanBeRetried() {
        assertThat(TrackingStatus.DELIVERY_FAILED.isTerminal()).isFalse();
    }
}
