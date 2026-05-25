CREATE TABLE IF NOT EXISTS idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(128) NOT NULL,
    transaction_id VARCHAR(128) NOT NULL,
    decision VARCHAR(64) NOT NULL,
    reason VARCHAR(512),
    decided_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE UNIQUE INDEX IF NOT EXISTS ux_idempotency_keys_correlation_id
    ON idempotency_keys (correlation_id);