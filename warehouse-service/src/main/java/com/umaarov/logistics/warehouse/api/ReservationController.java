package com.umaarov.logistics.warehouse.api;

import com.umaarov.logistics.warehouse.domain.ReservationResult;
import com.umaarov.logistics.warehouse.domain.ReservationService;
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
@RequestMapping("/internal/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        ReservationResult result = reservationService.reserve(request.orderId(), request.toDomainItems());
        ReservationResponse body = ReservationResponse.from(result.reservation());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/internal/reservations/" + request.orderId())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{orderId}")
    public ReservationResponse get(@PathVariable UUID orderId) {
        return ReservationResponse.from(reservationService.get(orderId));
    }

    @PostMapping("/{orderId}/release")
    public ReservationResponse release(@PathVariable UUID orderId) {
        return ReservationResponse.from(reservationService.release(orderId));
    }

    @PostMapping("/{orderId}/commit")
    public ReservationResponse commit(@PathVariable UUID orderId) {
        return ReservationResponse.from(reservationService.commit(orderId));
    }
}
