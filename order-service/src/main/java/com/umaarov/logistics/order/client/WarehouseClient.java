package com.umaarov.logistics.order.client;

import com.umaarov.logistics.order.domain.OrderLine;
import java.util.List;
import java.util.UUID;

public interface WarehouseClient {

    void reserve(UUID orderId, List<OrderLine> lines);

    void release(UUID orderId);

    void commit(UUID orderId);
}
