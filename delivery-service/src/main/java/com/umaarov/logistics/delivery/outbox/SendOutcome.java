package com.umaarov.logistics.delivery.outbox;

public enum SendOutcome {
    DELIVERED,
    REJECTED,
    RETRY
}
