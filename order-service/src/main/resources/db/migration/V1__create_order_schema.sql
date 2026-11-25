CREATE TABLE orders (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(64),
    tracking_number VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    delivery_status VARCHAR(16),
    rejection_reason VARCHAR(255),
    compensation_pending BOOLEAN NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    customer_phone VARCHAR(32) NOT NULL,
    city VARCHAR(80) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uq_orders_tracking_number UNIQUE (tracking_number),
    CONSTRAINT ck_orders_status CHECK (status IN (
        'PENDING', 'CONFIRMED', 'IN_DELIVERY', 'DELIVERED', 'CANCELLED', 'REJECTED'))
);

CREATE INDEX idx_orders_status_created ON orders (status, created_at);
CREATE INDEX idx_orders_compensation ON orders (compensation_pending, created_at);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uq_order_items_order_sku UNIQUE (order_id, sku),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0)
);
