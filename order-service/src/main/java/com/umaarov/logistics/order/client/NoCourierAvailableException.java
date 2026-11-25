package com.umaarov.logistics.order.client;

public class NoCourierAvailableException extends DownstreamException {

    public NoCourierAvailableException() {
        super("No courier with free capacity is available");
    }
}
