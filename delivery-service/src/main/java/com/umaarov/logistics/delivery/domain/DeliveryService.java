package com.umaarov.logistics.delivery.domain;

import com.umaarov.logistics.delivery.outbox.OutboxEventFactory;
import com.umaarov.logistics.delivery.outbox.OutboxRepository;
import com.umaarov.logistics.delivery.persistence.CourierRepository;
import com.umaarov.logistics.delivery.persistence.DeliveryRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

    private static final int ASSIGNMENT_CANDIDATES = 20;

    private final DeliveryRepository deliveries;
    private final CourierRepository couriers;
    private final OutboxRepository outbox;
    private final OutboxEventFactory events;
    private final Clock clock;

    public DeliveryService(
            DeliveryRepository deliveries,
            CourierRepository couriers,
            OutboxRepository outbox,
            OutboxEventFactory events,
            Clock clock) {
        this.deliveries = deliveries;
        this.couriers = couriers;
        this.outbox = outbox;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public DeliveryCreation create(CreateDeliveryCommand command) {
        var existing = deliveries.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            return new DeliveryCreation(existing.get(), false);
        }

        Instant now = clock.instant();
        Courier courier = assignCourier(now);
        Delivery delivery = deliveries.save(new Delivery(command, courier, now));
        outbox.save(events.tracking(delivery, DeliveryStatus.ASSIGNED, now));
        return new DeliveryCreation(delivery, true);
    }

    @Transactional(readOnly = true)
    public Delivery get(UUID id) {
        return deliveries.findById(id).orElseThrow(() -> new DeliveryNotFoundException(id.toString()));
    }

    @Transactional(readOnly = true)
    public Delivery getByOrder(UUID orderId) {
        return deliveries.findByOrderId(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException("for order " + orderId));
    }

    @Transactional(readOnly = true)
    public List<Delivery> list(UUID courierId, DeliveryStatus status, int page, int size) {
        Specification<Delivery> filter = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (courierId != null) {
                predicates.add(builder.equal(root.get("courier").get("id"), courierId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return deliveries.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    @Transactional
    public Delivery updateStatus(UUID id, DeliveryStatus next, String note) {
        return apply(get(id), next, note);
    }

    @Transactional
    public Delivery cancelByOrder(UUID orderId) {
        return apply(getByOrder(orderId), DeliveryStatus.CANCELLED, null);
    }

    private Delivery apply(Delivery delivery, DeliveryStatus next, String note) {
        if (delivery.getStatus() == next) {
            return delivery;
        }
        UUID deliveryId = delivery.getId();
        UUID courierId = delivery.getCourier().getId();
        Instant now = clock.instant();

        delivery.moveTo(next, note, now);
        outbox.save(events.tracking(delivery, next, now));
        if (events.notifiesOrder(next)) {
            outbox.save(events.order(delivery, next, now));
        }
        if (next.isFinal()) {
            couriers.freeSlot(courierId, now);
        }
        return deliveries.findById(deliveryId).orElseThrow();
    }

    private Courier assignCourier(Instant now) {
        List<Courier> candidates = couriers.findAssignable(PageRequest.of(0, ASSIGNMENT_CANDIDATES));
        for (Courier candidate : candidates) {
            if (couriers.takeSlot(candidate.getId(), now) == 1) {
                return couriers.findById(candidate.getId()).orElseThrow();
            }
        }
        throw new NoCourierAvailableException();
    }
}
