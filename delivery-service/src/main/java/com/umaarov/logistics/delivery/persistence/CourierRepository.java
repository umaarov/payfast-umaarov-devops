package com.umaarov.logistics.delivery.persistence;

import com.umaarov.logistics.delivery.domain.Courier;
import com.umaarov.logistics.delivery.domain.CourierStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourierRepository extends JpaRepository<Courier, UUID> {

    boolean existsByPhone(String phone);

    @Query("""
            select c from Courier c
            where c.status = com.umaarov.logistics.delivery.domain.CourierStatus.AVAILABLE
              and c.activeDeliveries < c.maxActiveDeliveries
            order by c.activeDeliveries asc, c.name asc
            """)
    List<Courier> findAssignable(Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Courier c
            set c.activeDeliveries = c.activeDeliveries + 1, c.updatedAt = :now
            where c.id = :id
              and c.status = com.umaarov.logistics.delivery.domain.CourierStatus.AVAILABLE
              and c.activeDeliveries < c.maxActiveDeliveries
            """)
    int takeSlot(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Courier c
            set c.activeDeliveries = c.activeDeliveries - 1, c.updatedAt = :now
            where c.id = :id and c.activeDeliveries > 0
            """)
    int freeSlot(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Courier c set c.status = :status, c.updatedAt = :now where c.id = :id")
    int changeStatus(@Param("id") UUID id, @Param("status") CourierStatus status, @Param("now") Instant now);
}
