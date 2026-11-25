package com.umaarov.logistics.delivery.outbox;

public record SendResult(SendOutcome outcome, String detail) {

    public static SendResult delivered() {
        return new SendResult(SendOutcome.DELIVERED, null);
    }

    public static SendResult rejected(String detail) {
        return new SendResult(SendOutcome.REJECTED, detail);
    }

    public static SendResult retry(String detail) {
        return new SendResult(SendOutcome.RETRY, detail);
    }
}
