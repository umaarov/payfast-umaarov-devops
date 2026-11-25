CREATE TABLE tracking_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    tracking_number VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    description VARCHAR(500),
    location VARCHAR(120),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_tracking_events_event_id UNIQUE (event_id),
    CONSTRAINT ck_tracking_events_status CHECK (status IN (
        'CREATED', 'COURIER_ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'DELIVERY_FAILED', 'CANCELLED'))
);

CREATE INDEX idx_tracking_events_timeline ON tracking_events (tracking_number, occurred_at, recorded_at);
