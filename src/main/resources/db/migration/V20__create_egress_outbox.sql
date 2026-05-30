CREATE TABLE egress_outbox (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(36) NOT NULL UNIQUE,
  event_type VARCHAR(128) NOT NULL,
  transaction_id VARCHAR(128) NOT NULL,
  correlation_id VARCHAR(128),
  payload_json TEXT NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_error VARCHAR(1024),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  processed_at TIMESTAMPTZ
);

CREATE INDEX idx_egress_outbox_status_next_attempt
    ON egress_outbox (status, next_attempt_at);

CREATE INDEX idx_egress_outbox_correlation_id
    ON egress_outbox (correlation_id);

-- Ogranicza przypadkowe duplikaty eventu biznesowego dla tego samego correlationId.
CREATE UNIQUE INDEX uk_egress_outbox_corr_event
    ON egress_outbox (correlation_id, event_type);