package com.umaarov.logistics.delivery.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop50ByTargetAndStateOrderByIdAsc(OutboxTarget target, OutboxState state);
}
