package com.umaarov.logistics.warehouse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private final Instant created = Instant.parse("2026-01-01T10:00:00Z");
    private final Instant later = Instant.parse("2026-01-01T11:00:00Z");

    private Reservation newReservation() {
        return new Reservation(UUID.randomUUID(), created);
    }

    @Test
    void newReservationIsReserved() {
        Reservation reservation = newReservation();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.getUpdatedAt()).isEqualTo(created);
    }

    @Test
    void releaseMovesReservedToReleased() {
        Reservation reservation = newReservation();

        reservation.release(later);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void commitMovesReservedToCommitted() {
        Reservation reservation = newReservation();

        reservation.commit(later);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void committedReservationCannotBeReleased() {
        Reservation reservation = newReservation();
        reservation.commit(later);

        assertThatThrownBy(() -> reservation.release(later)).isInstanceOf(IllegalReservationStateException.class);
    }

    @Test
    void releasedReservationCannotBeCommitted() {
        Reservation reservation = newReservation();
        reservation.release(later);

        assertThatThrownBy(() -> reservation.commit(later)).isInstanceOf(IllegalReservationStateException.class);
    }

    @Test
    void productAvailabilityIsOnHandMinusReserved() {
        Product product = new Product("SKU-1", "Box", 10, created);

        assertThat(product.getAvailable()).isEqualTo(10);
        assertThat(product.getQuantityReserved()).isZero();
    }
}
