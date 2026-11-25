package com.umaarov.logistics.order.client;

import java.util.UUID;

public interface DeliveryClient {

    void create(DeliveryRequest request);

    CancelResult cancel(UUID orderId);
}
