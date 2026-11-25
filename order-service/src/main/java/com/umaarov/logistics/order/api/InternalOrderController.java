package com.umaarov.logistics.order.api;

import com.umaarov.logistics.order.domain.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/{orderId}/delivery-events")
    public OrderResponse deliveryEvent(@PathVariable UUID orderId, @Valid @RequestBody DeliveryEventRequest request) {
        return OrderResponse.from(orderService.onDeliveryEvent(orderId, request.status()));
    }
}
