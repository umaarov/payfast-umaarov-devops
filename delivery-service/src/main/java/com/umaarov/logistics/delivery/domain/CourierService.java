package com.umaarov.logistics.delivery.domain;

import com.umaarov.logistics.delivery.persistence.CourierRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourierService {

    public static final int DEFAULT_CAPACITY = 5;

    private final CourierRepository couriers;
    private final Clock clock;

    public CourierService(CourierRepository couriers, Clock clock) {
        this.couriers = couriers;
        this.clock = clock;
    }

    @Transactional
    public Courier create(String name, String phone, Integer maxActiveDeliveries) {
        if (couriers.existsByPhone(phone)) {
            throw new DuplicateCourierException();
        }
        int capacity = maxActiveDeliveries != null ? maxActiveDeliveries : DEFAULT_CAPACITY;
        return couriers.save(new Courier(name, phone, capacity, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Courier get(UUID id) {
        return couriers.findById(id).orElseThrow(() -> new CourierNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Courier> list(int page, int size) {
        return couriers.findAll(PageRequest.of(page, size, Sort.by("name"))).getContent();
    }

    @Transactional
    public Courier setAvailability(UUID id, boolean available) {
        get(id);
        CourierStatus status = available ? CourierStatus.AVAILABLE : CourierStatus.OFF_DUTY;
        couriers.changeStatus(id, status, clock.instant());
        return get(id);
    }
}
