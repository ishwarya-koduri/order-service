-- V1: Initial schema for Order Processing Service
-- Creates all tables in dependency order (referenced tables first)

CREATE TABLE orders
(
    id             UUID                        NOT NULL,
    customer_id    UUID                        NOT NULL,
    status         VARCHAR(30)                 NOT NULL,
    total_amount   NUMERIC(12, 2)              NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    version        INTEGER                     NOT NULL DEFAULT 0,

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT chk_orders_status CHECK (
        status IN (
            'PENDING',
            'CONFIRMED',
            'PREPARING',
            'OUT_FOR_DELIVERY',
            'DELIVERED',
            'CANCELLED'
        )
    ),
    CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0)
);

CREATE TABLE order_items
(
    id             UUID                        NOT NULL,
    order_id       UUID                        NOT NULL,
    menu_item_id   VARCHAR(100)                NOT NULL,
    name           VARCHAR(255)                NOT NULL,
    quantity       INTEGER                     NOT NULL,
    unit_price     NUMERIC(10, 2)              NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT chk_order_items_quantity CHECK (quantity >= 1),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0)
);

CREATE TABLE idempotency_keys
(
    idempotency_key VARCHAR(255)                NOT NULL,
    order_id        UUID,
    http_status     INTEGER                     NOT NULL,
    response_body   TEXT                        NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_idempotency_keys PRIMARY KEY (idempotency_key)
);

CREATE TABLE outbox_events
(
    id              UUID                        NOT NULL,
    order_id        UUID                        NOT NULL,
    event_type      VARCHAR(100)                NOT NULL,
    payload         TEXT                        NOT NULL,
    published       BOOLEAN                     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT fk_outbox_events_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE TABLE delivery_notifications
(
    id               UUID                       NOT NULL,
    order_id         UUID                       NOT NULL,
    customer_id      UUID                       NOT NULL,
    event_id         VARCHAR(255)               NOT NULL,
    previous_status  VARCHAR(30),
    new_status       VARCHAR(30)                NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_delivery_notifications PRIMARY KEY (id),
    CONSTRAINT fk_delivery_notifications_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT uq_delivery_notifications_event_id UNIQUE (event_id)
);