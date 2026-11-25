CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    quantity_on_hand INTEGER NOT NULL,
    quantity_reserved INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_on_hand_non_negative CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_products_reserved_non_negative CHECK (quantity_reserved >= 0),
    CONSTRAINT ck_products_reserved_within_on_hand CHECK (quantity_reserved <= quantity_on_hand)
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_reservations_order UNIQUE (order_id),
    CONSTRAINT ck_reservations_status CHECK (status IN ('RESERVED', 'RELEASED', 'COMMITTED'))
);

CREATE TABLE reservation_lines (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_lines_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_lines_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_lines_reservation_product UNIQUE (reservation_id, product_id),
    CONSTRAINT ck_lines_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_lines_product ON reservation_lines (product_id);
