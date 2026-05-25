CREATE TABLE IF NOT EXISTS decision_log (
    id             BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128),
    decision       VARCHAR(64)  NOT NULL,
    reason         VARCHAR(512),
    decided_at     TIMESTAMP    NOT NULL,
    logged_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- Szybkie wyszukiwanie po correlationId (do audytu i testów)
CREATE INDEX IF NOT EXISTS ix_decision_log_correlation_id
    ON decision_log (correlation_id);

-- Szybkie wyszukiwanie po decision (np. ile REJECT w oknie czasu)
CREATE INDEX IF NOT EXISTS ix_decision_log_decision
    ON decision_log (decision);