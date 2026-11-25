package com.umaarov.logistics.warehouse.domain;

import com.umaarov.logistics.warehouse.persistence.ProductRepository;
import com.umaarov.logistics.warehouse.persistence.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final Clock clock;

    public ReservationService(ProductRepository products, ReservationRepository reservations, Clock clock) {
        this.products = products;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Transactional
    public ReservationResult reserve(UUID orderId, List<ReservationItem> items) {
        var existing = reservations.findByOrderId(orderId);
        if (existing.isPresent()) {
            return new ReservationResult(existing.get(), false);
        }

        Map<String, Integer> requested = new TreeMap<>();
        for (ReservationItem item : items) {
            requested.merge(item.sku(), item.quantity(), Integer::sum);
        }

        Instant now = clock.instant();
        Map<UUID, Integer> reserved = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            Product product = products.findBySku(entry.getKey())
                    .orElseThrow(() -> new ProductNotFoundException(entry.getKey()));
            int updated = products.reserve(product.getId(), entry.getValue(), now);
            if (updated == 0) {
                int available = products.findById(product.getId()).map(Product::getAvailable).orElse(0);
                throw new InsufficientStockException(entry.getKey(), entry.getValue(), available);
            }
            reserved.put(product.getId(), entry.getValue());
        }

        Reservation reservation = new Reservation(orderId, now);
        reserved.forEach((productId, quantity) ->
                reservation.addLine(products.findById(productId).orElseThrow(), quantity));
        return new ReservationResult(reservations.save(reservation), true);
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID orderId) {
        return reservations.findByOrderId(orderId).orElseThrow(() -> new ReservationNotFoundException(orderId));
    }

    @Transactional
    public Reservation release(UUID orderId) {
        Reservation reservation = get(orderId);
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return reservation;
        }
        Instant now = clock.instant();
        reservation.release(now);
        for (ReservationLine line : reservation.getLines()) {
            requireUpdated(products.release(line.getProduct().getId(), line.getQuantity(), now), line);
        }
        return get(orderId);
    }

    @Transactional
    public Reservation commit(UUID orderId) {
        Reservation reservation = get(orderId);
        if (reservation.getStatus() == ReservationStatus.COMMITTED) {
            return reservation;
        }
        Instant now = clock.instant();
        reservation.commit(now);
        for (ReservationLine line : reservation.getLines()) {
            requireUpdated(products.dispatch(line.getProduct().getId(), line.getQuantity(), now), line);
        }
        return get(orderId);
    }

    private void requireUpdated(int updated, ReservationLine line) {
        if (updated == 0) {
            throw new IllegalStateException("Stock counters are inconsistent for sku " + line.getProduct().getSku());
        }
    }
}
