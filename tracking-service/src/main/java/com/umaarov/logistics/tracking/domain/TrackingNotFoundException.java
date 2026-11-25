package com.umaarov.logistics.tracking.domain;

public class TrackingNotFoundException extends RuntimeException {

    public TrackingNotFoundException(String trackingNumber) {
        super("No parcel found for tracking number " + trackingNumber);
    }
}
