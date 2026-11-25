package com.umaarov.logistics.warehouse.persistence;

import com.umaarov.logistics.warehouse.domain.Reservation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderId(UUID orderId);
}
