package com.umaarov.logistics.delivery.domain;

public class NoCourierAvailableException extends RuntimeException {

    public NoCourierAvailableException() {
        super("No courier with free capacity is available");
    }
}
