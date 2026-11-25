package com.umaarov.logistics.tracking.domain;

public class InvalidTrackingTransitionException extends RuntimeException {

    public InvalidTrackingTransitionException(String message) {
        super(message);
    }
}
