package com.umaarov.logistics.order.api;

import com.umaarov.logistics.order.domain.InvalidRequestException;
import com.umaarov.logistics.order.domain.OrderService;
import com.umaarov.logistics.order.domain.OrderStatus;
import com.umaarov.logistics.order.domain.PlacementResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final int MAX_KEY_LENGTH = 64;

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        String key = normalize(idempotencyKey);
        PlacementResult result = orderService.place(request.toCommand(key));
        OrderResponse body = OrderResponse.from(result.order());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/orders/" + body.id())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public List<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return orderService.list(status, page, size).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.get(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(orderService.cancel(id));
    }

    private String normalize(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new InvalidRequestException("Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        return key;
    }
}
