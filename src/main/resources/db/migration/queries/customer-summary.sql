-- ============================================================
-- Customer Order Summary
-- Returns customers who have placed more than 2 orders with:
--   - Total number of orders placed
--   - Total amount spent across all orders
--   - Status of their most recent order
-- ============================================================

SELECT
    o.customer_id,
    COUNT(o.id)                                         AS total_orders,
    SUM(o.total_amount)                                 AS total_amount_spent,
    (
        SELECT o2.status
        FROM orders o2
        WHERE o2.customer_id = o.customer_id
        ORDER BY o2.created_at DESC
        LIMIT 1
    )                                                   AS most_recent_order_status
FROM orders o
GROUP BY o.customer_id
HAVING COUNT(o.id) > 2
ORDER BY total_amount_spent DESC;