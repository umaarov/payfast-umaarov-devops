package com.umaarov.logistics.delivery.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxTarget target;

    @Column(nullable = false, length = 200)
    private String path;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxState state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(OutboxTarget target, String path, String payload, Instant now) {
        this.target = target;
        this.path = path;
        this.payload = payload;
        this.state = OutboxState.PENDING;
        this.attempts = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        attempts++;
        state = OutboxState.SENT;
        lastError = null;
        updatedAt = now;
    }

    public void markRejected(String reason, Instant now) {
        attempts++;
        state = OutboxState.REJECTED;
        lastError = truncate(reason);
        updatedAt = now;
    }

    public void recordFailedAttempt(String reason, Instant now) {
        attempts++;
        lastError = truncate(reason);
        updatedAt = now;
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_ERROR_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_ERROR_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public OutboxTarget getTarget() {
        return target;
    }

    public String getPath() {
        return path;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxState getState() {
        return state;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
