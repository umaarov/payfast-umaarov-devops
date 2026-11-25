package com.umaarov.logistics.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "couriers")
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CourierStatus status;

    @Column(name = "max_active_deliveries", nullable = false)
    private int maxActiveDeliveries;

    @Column(name = "active_deliveries", nullable = false)
    private int activeDeliveries;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Courier() {
    }

    public Courier(String name, String phone, int maxActiveDeliveries, Instant now) {
        this.name = name;
        this.phone = phone;
        this.status = CourierStatus.AVAILABLE;
        this.maxActiveDeliveries = maxActiveDeliveries;
        this.activeDeliveries = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public CourierStatus getStatus() {
        return status;
    }

    public int getMaxActiveDeliveries() {
        return maxActiveDeliveries;
    }

    public int getActiveDeliveries() {
        return activeDeliveries;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
