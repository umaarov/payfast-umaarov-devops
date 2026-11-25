package com.umaarov.logistics.order.domain;

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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 32)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "delivery_status", length = 16)
    private String deliveryStatus;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "compensation_pending", nullable = false)
    private boolean compensationPending;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 32)
    private String customerPhone;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public static Order pending(PlaceOrderCommand command, String trackingNumber, Instant now) {
        Order order = new Order();
        order.idempotencyKey = command.idempotencyKey();
        order.trackingNumber = trackingNumber;
        order.status = OrderStatus.PENDING;
        order.customerName = command.customerName();
        order.customerPhone = command.customerPhone();
        order.city = command.city();
        order.addressLine = command.addressLine();
        order.createdAt = now;
        order.updatedAt = now;
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (OrderLine line : command.lines()) {
            merged.merge(line.sku(), line.quantity(), Integer::sum);
        }
        merged.forEach((sku, quantity) -> order.items.add(new OrderItem(order, sku, quantity)));
        return order;
    }

    public List<OrderLine> lines() {
        return items.stream().map(item -> new OrderLine(item.getSku(), item.getQuantity())).toList();
    }

    public void confirm(Instant now) {
        require(OrderStatus.PENDING, "be confirmed");
        status = OrderStatus.CONFIRMED;
        touch(now);
    }

    public void reject(String reason, boolean compensationIncomplete, Instant now) {
        require(OrderStatus.PENDING, "be rejected");
        status = OrderStatus.REJECTED;
        rejectionReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 255));
        compensationPending = compensationIncomplete;
        touch(now);
    }

    public void cancel(boolean compensationIncomplete, Instant now) {
        require(OrderStatus.CONFIRMED, "be cancelled");
        status = OrderStatus.CANCELLED;
        compensationPending = compensationIncomplete;
        touch(now);
    }

    public void applyDeliveryStatus(DeliveryEventStatus event, Instant now) {
        if (status == OrderStatus.DELIVERED) {
            return;
        }
        if (status != OrderStatus.CONFIRMED && status != OrderStatus.IN_DELIVERY) {
            throw new IllegalOrderStateException(id, status, "accept delivery event " + event);
        }
        status = event == DeliveryEventStatus.DELIVERED ? OrderStatus.DELIVERED : OrderStatus.IN_DELIVERY;
        deliveryStatus = event.name();
        touch(now);
    }

    public void markCompensationDone(Instant now) {
        compensationPending = false;
        touch(now);
    }

    public boolean acceptsDeliveryEvents() {
        return status == OrderStatus.CONFIRMED || status == OrderStatus.IN_DELIVERY || status == OrderStatus.DELIVERED;
    }

    private void require(OrderStatus expected, String action) {
        if (status != expected) {
            throw new IllegalOrderStateException(id, status, action);
        }
    }

    private void touch(Instant now) {
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public boolean isCompensationPending() {
        return compensationPending;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getCity() {
        return city;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
