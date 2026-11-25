package com.umaarov.logistics.order.client;

public class UnknownProductException extends DownstreamException {

    public UnknownProductException(String detail) {
        super(detail);
    }
}
