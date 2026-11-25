package com.umaarov.logistics.order.domain;

import com.umaarov.logistics.order.client.CancelResult;
import com.umaarov.logistics.order.client.DeliveryClient;
import com.umaarov.logistics.order.client.DeliveryNotCancellableException;
import com.umaarov.logistics.order.client.DeliveryRequest;
import com.umaarov.logistics.order.client.DownstreamException;
import com.umaarov.logistics.order.client.DownstreamUnavailableException;
import com.umaarov.logistics.order.client.InsufficientStockException;
import com.umaarov.logistics.order.client.NoCourierAvailableException;
import com.umaarov.logistics.order.client.TrackingClient;
import com.umaarov.logistics.order.client.TrackingEventRequest;
import com.umaarov.logistics.order.client.UnknownProductException;
import com.umaarov.logistics.order.client.WarehouseClient;
import com.umaarov.logistics.order.persistence.OrderRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int EVENT_RETRIES = 3;
    private static final String TIMEOUT_REASON = "Order processing timed out";

    private final OrderRepository orders;
    private final WarehouseClient warehouse;
    private final DeliveryClient delivery;
    private final TrackingClient tracking;
    private final TrackingNumberGenerator trackingNumbers;
    private final Clock clock;

    public OrderService(
            OrderRepository orders,
            WarehouseClient warehouse,
            DeliveryClient delivery,
            TrackingClient tracking,
            TrackingNumberGenerator trackingNumbers,
            Clock clock) {
        this.orders = orders;
        this.warehouse = warehouse;
        this.delivery = delivery;
        this.tracking = tracking;
        this.trackingNumbers = trackingNumbers;
        this.clock = clock;
    }

    public PlacementResult place(PlaceOrderCommand command) {
        String key = command.idempotencyKey();
        if (key != null) {
            Optional<Order> existing = orders.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return new PlacementResult(existing.get(), false);
            }
        }

        Order order;
        try {
            order = orders.save(Order.pending(command, trackingNumbers.next(), clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            Optional<Order> existing = key == null ? Optional.empty() : orders.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return new PlacementResult(existing.get(), false);
            }
            throw exception;
        }

        try {
            tracking.record(trackingEvent(order, "created", "CREATED", "Order accepted"));
            warehouse.reserve(order.getId(), order.lines());
            delivery.create(new DeliveryRequest(
                    order.getId(),
                    order.getTrackingNumber(),
                    order.getCustomerName(),
                    order.getCustomerPhone(),
                    order.getCity(),
                    order.getAddressLine()));
        } catch (InsufficientStockException exception) {
            throw reject(order, RejectionReason.INSUFFICIENT_STOCK, exception.getMessage());
        } catch (UnknownProductException exception) {
            throw reject(order, RejectionReason.UNKNOWN_PRODUCT, exception.getMessage());
        } catch (NoCourierAvailableException exception) {
            throw reject(order, RejectionReason.NO_COURIER_AVAILABLE, exception.getMessage());
        } catch (DownstreamException exception) {
            throw reject(order, RejectionReason.SERVICE_UNAVAILABLE, exception.getMessage());
        }

        order.confirm(clock.instant());
        return new PlacementResult(orders.save(order), true);
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Order> list(OrderStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return (status == null ? orders.findAll(pageRequest) : orders.findByStatus(status, pageRequest)).getContent();
    }

    public Order cancel(UUID id) {
        Order order = get(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalOrderStateException(id, order.getStatus(), "be cancelled");
        }
        try {
            delivery.cancel(id);
        } catch (DeliveryNotCancellableException exception) {
            throw new IllegalOrderStateException(id, order.getStatus(), "be cancelled: the parcel was already picked up");
        }
        boolean incomplete = releaseStock(id);
        order.cancel(incomplete, clock.instant());
        return orders.save(order);
    }

    public Order onDeliveryEvent(UUID orderId, DeliveryEventStatus event) {
        for (int attempt = 1; ; attempt++) {
            try {
                return applyDeliveryEvent(orderId, event);
            } catch (OptimisticLockingFailureException exception) {
                if (attempt >= EVENT_RETRIES) {
                    throw exception;
                }
            }
        }
    }

    public void rejectStale(UUID orderId) {
        Order order = get(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        boolean incomplete = compensate(order);
        order.reject(TIMEOUT_REASON, incomplete, clock.instant());
        orders.save(order);
    }

    public void retryCompensation(UUID orderId) {
        Order order = get(orderId);
        if (!order.isCompensationPending()) {
            return;
        }
        if (!compensate(order)) {
            order.markCompensationDone(clock.instant());
            orders.save(order);
        }
    }

    private Order applyDeliveryEvent(UUID orderId, DeliveryEventStatus event) {
        Order order = get(orderId);
        if (!order.acceptsDeliveryEvents()) {
            throw new IllegalOrderStateException(orderId, order.getStatus(), "accept delivery event " + event);
        }
        boolean stockLeaves = event == DeliveryEventStatus.PICKED_UP || event == DeliveryEventStatus.DELIVERED;
        if (stockLeaves && order.getStatus() != OrderStatus.DELIVERED) {
            warehouse.commit(orderId);
        }
        order.applyDeliveryStatus(event, clock.instant());
        return orders.save(order);
    }

    private OrderRejectedException reject(Order order, RejectionReason reason, String detail) {
        boolean incomplete = compensate(order);
        order.reject(detail, incomplete, clock.instant());
        orders.save(order);
        return new OrderRejectedException(order.getId(), reason, detail);
    }

    private boolean compensate(Order order) {
        boolean incomplete = false;
        CancelResult cancelled = null;
        try {
            cancelled = delivery.cancel(order.getId());
        } catch (DeliveryNotCancellableException exception) {
            log.error("Order {} has a delivery that cannot be cancelled", order.getId());
        } catch (DownstreamUnavailableException exception) {
            incomplete = true;
            log.warn("Delivery of order {} could not be cancelled yet: {}", order.getId(), exception.getMessage());
        } catch (DownstreamException exception) {
            log.error("Delivery of order {} was rejected: {}", order.getId(), exception.getMessage());
        }
        incomplete |= releaseStock(order.getId());
        if (cancelled == CancelResult.NOT_FOUND) {
            incomplete |= cancelTracking(order);
        }
        return incomplete;
    }

    private boolean releaseStock(UUID orderId) {
        try {
            warehouse.release(orderId);
            return false;
        } catch (DownstreamUnavailableException exception) {
            log.warn("Stock of order {} could not be released yet: {}", orderId, exception.getMessage());
            return true;
        } catch (DownstreamException exception) {
            log.error("Stock release of order {} was rejected: {}", orderId, exception.getMessage());
            return false;
        }
    }

    private boolean cancelTracking(Order order) {
        try {
            tracking.record(trackingEvent(order, "cancelled", "CANCELLED", "Order cancelled"));
            return false;
        } catch (DownstreamUnavailableException exception) {
            log.warn("Tracking of order {} could not be closed yet: {}", order.getId(), exception.getMessage());
            return true;
        } catch (DownstreamException exception) {
            log.warn("Tracking of order {} was not closed: {}", order.getId(), exception.getMessage());
            return false;
        }
    }

    private TrackingEventRequest trackingEvent(Order order, String kind, String status, String description) {
        UUID eventId = UUID.nameUUIDFromBytes((kind + ":" + order.getId()).getBytes(StandardCharsets.UTF_8));
        return new TrackingEventRequest(eventId, order.getTrackingNumber(), status, description, order.getCity());
    }
}
