-- 1) Dedup aktywnych rekordów: zostawiamy najstarszy (najmniejszy db_id), resztę soft-delete
WITH ranked AS (
    SELECT
        db_id,
        ROW_NUMBER() OVER (
            PARTITION BY owner_username, original_id
            ORDER BY db_id ASC
        ) AS rn
    FROM transactions
    WHERE deleted = false
      AND owner_username IS NOT NULL
      AND original_id IS NOT NULL
)
UPDATE transactions t
SET
    deleted = true,
    deleted_at = CURRENT_TIMESTAMP,
    deleted_by = 'flyway_v17_dedup'
    FROM ranked r
WHERE t.db_id = r.db_id
  AND r.rn > 1;

-- 2) Twarda idempotencja dla aktywnych rekordów
CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_owner_original_active
    ON transactions(owner_username, original_id)
    WHERE deleted = false
    AND owner_username IS NOT NULL
    AND original_id IS NOT NULL;