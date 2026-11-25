package com.umaarov.logistics.warehouse.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<ReservationLine> lines = new ArrayList<>();

    protected Reservation() {
    }

    public Reservation(UUID orderId, Instant now) {
        this.orderId = orderId;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addLine(Product product, int quantity) {
        lines.add(new ReservationLine(this, product, quantity));
    }

    public void release(Instant now) {
        requireReserved("release");
        status = ReservationStatus.RELEASED;
        updatedAt = now;
    }

    public void commit(Instant now) {
        requireReserved("commit");
        status = ReservationStatus.COMMITTED;
        updatedAt = now;
    }

    private void requireReserved(String action) {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalReservationStateException(orderId, status, action);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ReservationLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
