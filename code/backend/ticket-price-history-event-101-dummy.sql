-- Dummy price history for existing tickets of event 101.
-- Keeps tickets.price unchanged. Re-running skips matching ticket/time entries.
USE free_ticket;
START TRANSACTION;

INSERT INTO ticket_price_history (ticket_id, price, changed_at)
SELECT t.id,
       CASE WHEN sample.days_before = 1 THEN t.price
            ELSE CAST(ROUND(t.price * sample.price_ratio / 1000) * 1000 AS SIGNED)
       END,
       DATE_SUB(t.start_time, INTERVAL sample.days_before DAY)
FROM tickets t
CROSS JOIN (
    SELECT 30 AS days_before, 0.80 AS price_ratio
    UNION ALL SELECT 21, 0.90
    UNION ALL SELECT 14, 1.05
    UNION ALL SELECT 7, 0.95
    UNION ALL SELECT 1, 1.00
) sample
WHERE t.event_id = 101
  AND t.price IS NOT NULL
  AND t.price >= 0
  AND t.start_time IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM ticket_price_history h
      WHERE h.ticket_id = t.id
        AND h.changed_at = DATE_SUB(t.start_time, INTERVAL sample.days_before DAY)
  )
ORDER BY t.id, sample.days_before DESC;

SELECT ROW_COUNT() AS inserted_rows;
COMMIT;

SELECT t.id AS ticket_id, t.price AS current_price, h.price AS history_price, h.changed_at
FROM ticket_price_history h
JOIN tickets t ON t.id = h.ticket_id
WHERE t.event_id = 101
ORDER BY t.id, h.changed_at;
