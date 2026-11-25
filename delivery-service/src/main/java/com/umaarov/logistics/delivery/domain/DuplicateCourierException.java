package com.umaarov.logistics.delivery.domain;

public class DuplicateCourierException extends RuntimeException {

    public DuplicateCourierException() {
        super("A courier with this phone number already exists");
    }
}
