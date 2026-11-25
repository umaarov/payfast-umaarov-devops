package com.umaarov.logistics.delivery.api;

import com.umaarov.logistics.delivery.domain.Courier;
import com.umaarov.logistics.delivery.domain.CourierService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delivery/couriers")
public class CourierController {

    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @PostMapping
    public ResponseEntity<CourierResponse> create(@Valid @RequestBody CreateCourierRequest request) {
        Courier courier = courierService.create(request.name(), request.phone(), request.maxActiveDeliveries());
        return ResponseEntity
                .created(URI.create("/api/delivery/couriers/" + courier.getId()))
                .body(CourierResponse.from(courier));
    }

    @GetMapping
    public List<CourierResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return courierService.list(page, size).stream().map(CourierResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CourierResponse get(@PathVariable UUID id) {
        return CourierResponse.from(courierService.get(id));
    }

    @PostMapping("/{id}/availability")
    public CourierResponse setAvailability(@PathVariable UUID id, @Valid @RequestBody AvailabilityRequest request) {
        return CourierResponse.from(courierService.setAvailability(id, request.available()));
    }
}
