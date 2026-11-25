package com.umaarov.logistics.order.client;

public class DeliveryNotCancellableException extends DownstreamException {

    public DeliveryNotCancellableException() {
        super("The delivery was already picked up and cannot be cancelled");
    }
}
