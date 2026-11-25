package com.umaarov.logistics.order.client;

public class InsufficientStockException extends DownstreamException {

    public InsufficientStockException(String sku, int requested, int available) {
        super("Insufficient stock for sku " + sku + ": requested " + requested + ", available " + available);
    }
}
