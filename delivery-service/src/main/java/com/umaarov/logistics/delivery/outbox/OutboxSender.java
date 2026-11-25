package com.umaarov.logistics.delivery.outbox;

public interface OutboxSender {

    SendResult send(OutboxEvent event);
}
