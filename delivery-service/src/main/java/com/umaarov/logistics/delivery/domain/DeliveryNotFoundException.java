package com.umaarov.logistics.delivery.domain;

public class DeliveryNotFoundException extends RuntimeException {

    public DeliveryNotFoundException(String reference) {
        super("Delivery " + reference + " does not exist");
    }
}
