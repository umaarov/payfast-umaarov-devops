package com.umaarov.logistics.order.persistence;

import com.umaarov.logistics.order.domain.Order;
import com.umaarov.logistics.order.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    List<Order> findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus status, Instant createdBefore);

    List<Order> findTop50ByCompensationPendingTrueOrderByCreatedAtAsc();
}
