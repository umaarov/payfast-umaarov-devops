package com.umaarov.logistics.delivery.domain;

import java.util.UUID;

public class CourierNotFoundException extends RuntimeException {

    public CourierNotFoundException(UUID id) {
        super("Courier " + id + " does not exist");
    }
}
