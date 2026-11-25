package com.umaarov.logistics.delivery.api;

import com.umaarov.logistics.delivery.domain.DeliveryService;
import com.umaarov.logistics.delivery.domain.DeliveryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delivery/deliveries")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public List<DeliveryResponse> list(
            @RequestParam(required = false) UUID courierId,
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return deliveryService.list(courierId, status, page, size).stream().map(DeliveryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DeliveryResponse get(@PathVariable UUID id) {
        return DeliveryResponse.from(deliveryService.get(id));
    }

    @PostMapping("/{id}/status")
    public DeliveryResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return DeliveryResponse.from(deliveryService.updateStatus(id, request.status(), request.note()));
    }
}
