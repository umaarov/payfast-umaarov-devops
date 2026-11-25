package com.umaarov.logistics.delivery.api;

import com.umaarov.logistics.delivery.domain.DeliveryCreation;
import com.umaarov.logistics.delivery.domain.DeliveryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/deliveries")
public class InternalDeliveryController {

    private final DeliveryService deliveryService;

    public InternalDeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@Valid @RequestBody CreateDeliveryRequest request) {
        DeliveryCreation creation = deliveryService.create(request.toCommand());
        DeliveryResponse body = DeliveryResponse.from(creation.delivery());
        if (creation.created()) {
            return ResponseEntity.created(URI.create("/internal/deliveries/by-order/" + request.orderId())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/by-order/{orderId}")
    public DeliveryResponse getByOrder(@PathVariable UUID orderId) {
        return DeliveryResponse.from(deliveryService.getByOrder(orderId));
    }

    @PostMapping("/by-order/{orderId}/cancel")
    public DeliveryResponse cancel(@PathVariable UUID orderId) {
        return DeliveryResponse.from(deliveryService.cancelByOrder(orderId));
    }
}
