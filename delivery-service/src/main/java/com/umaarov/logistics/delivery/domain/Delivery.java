package com.umaarov.logistics.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 32)
    private String trackingNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private Courier courier;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 32)
    private String recipientPhone;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Delivery() {
    }

    public Delivery(CreateDeliveryCommand command, Courier courier, Instant now) {
        this.orderId = command.orderId();
        this.trackingNumber = command.trackingNumber();
        this.courier = courier;
        this.recipientName = command.recipientName();
        this.recipientPhone = command.recipientPhone();
        this.city = command.city();
        this.addressLine = command.addressLine();
        this.status = DeliveryStatus.ASSIGNED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void moveTo(DeliveryStatus next, String note, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new IllegalDeliveryTransitionException(orderId, status, next);
        }
        status = next;
        failureReason = next == DeliveryStatus.FAILED ? note : null;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public Courier getCourier() {
        return courier;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getCity() {
        return city;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
