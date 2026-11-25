package com.umaarov.logistics.order.client;

public interface TrackingClient {

    void record(TrackingEventRequest request);
}
