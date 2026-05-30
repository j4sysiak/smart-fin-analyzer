CREATE UNIQUE INDEX IF NOT EXISTS uk_decision_log_correlation_id
    ON decision_log (correlation_id)
    WHERE correlation_id IS NOT NULL;