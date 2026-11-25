package com.umaarov.logistics.warehouse.persistence;

import com.umaarov.logistics.warehouse.domain.Product;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Product p
            set p.quantityOnHand = p.quantityOnHand + :quantity, p.updatedAt = :now
            where p.id = :id
            """)
    int receive(@Param("id") UUID id, @Param("quantity") int quantity, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Product p
            set p.quantityReserved = p.quantityReserved + :quantity, p.updatedAt = :now
            where p.id = :id and p.quantityOnHand - p.quantityReserved >= :quantity
            """)
    int reserve(@Param("id") UUID id, @Param("quantity") int quantity, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Product p
            set p.quantityReserved = p.quantityReserved - :quantity, p.updatedAt = :now
            where p.id = :id and p.quantityReserved >= :quantity
            """)
    int release(@Param("id") UUID id, @Param("quantity") int quantity, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Product p
            set p.quantityOnHand = p.quantityOnHand - :quantity,
                p.quantityReserved = p.quantityReserved - :quantity,
                p.updatedAt = :now
            where p.id = :id and p.quantityReserved >= :quantity
            """)
    int dispatch(@Param("id") UUID id, @Param("quantity") int quantity, @Param("now") Instant now);
}
