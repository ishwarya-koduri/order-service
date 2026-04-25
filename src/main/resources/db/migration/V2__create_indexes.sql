-- ══════════════════════════════════════════════════════════════════════════
-- V2: Indexes
-- Separated from table creation intentionally.
-- In production, indexes can be created CONCURRENTLY without locking tables.
-- Keeping them in a separate migration makes this possible later.
-- ══════════════════════════════════════════════════════════════════════════


-- ──────────────────────────────────────────────────────────────────────────
-- INDEXES ON: orders
-- ──────────────────────────────────────────────────────────────────────────

-- GET /orders?customerId=... — most common query pattern
-- Without this index, every list query does a full table scan.
CREATE INDEX idx_orders_customer_id
    ON orders (customer_id);

-- GET /orders?status=... — filter by status
CREATE INDEX idx_orders_status
    ON orders (status);

-- GET /orders?customerId=...&status=... — combined filter
-- Composite index is more efficient than two separate indexes for this query.
-- Column order matters: customerId first because it has higher cardinality.
CREATE INDEX idx_orders_customer_id_status
    ON orders (customer_id, status);

-- Sorting by created_at DESC is the default order for list queries.
CREATE INDEX idx_orders_created_at
    ON orders (created_at DESC);


-- ──────────────────────────────────────────────────────────────────────────
-- INDEXES ON: order_items
-- ──────────────────────────────────────────────────────────────────────────

-- Fetching items for a specific order — used every time we load an order.
CREATE INDEX idx_order_items_order_id
    ON order_items (order_id);


-- ──────────────────────────────────────────────────────────────────────────
-- INDEXES ON: outbox_events
-- ──────────────────────────────────────────────────────────────────────────

-- THE most important index in the entire schema.
--
-- The OutboxPoller runs every 5 seconds and queries:
--   SELECT * FROM outbox_events WHERE published = false ...
--
-- A regular index on (published) would still scan all published rows
-- because FALSE is a low-cardinality value.
--
-- A PARTIAL INDEX only indexes rows WHERE published = false.
-- Once a row is published (published = true), it falls out of this index.
-- The index stays tiny regardless of how many total rows exist.
-- This makes the poller query extremely fast even at millions of events.
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at ASC)
    WHERE published = false;


-- ──────────────────────────────────────────────────────────────────────────
-- INDEXES ON: delivery_notifications
-- ──────────────────────────────────────────────────────────────────────────

-- Lookup by order_id when querying notification history.
CREATE INDEX idx_delivery_notifications_order_id
    ON delivery_notifications (order_id);


-- ──────────────────────────────────────────────────────────────────────────
-- COMMENT: Why indexes are in a separate migration file
-- ──────────────────────────────────────────────────────────────────────────
-- In production PostgreSQL, CREATE INDEX locks the table for writes
-- by default. To avoid downtime, you use CREATE INDEX CONCURRENTLY
-- which does NOT lock the table but CANNOT run inside a transaction.
--
-- Flyway runs each migration inside a transaction by default.
-- To use CONCURRENTLY in production, you would:
--   1. Add `-- flyway:nonTransactional` annotation to the migration
--   2. Change CREATE INDEX to CREATE INDEX CONCURRENTLY
--
-- For this assignment, regular CREATE INDEX is fine.
-- But separating indexes from table creation means this upgrade
-- costs one line change — not a full schema rewrite.
-- ══════════════════════════════════════════════════════════════════════════