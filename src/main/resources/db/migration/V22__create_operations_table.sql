CREATE TABLE IF NOT EXISTS operations (
    id                 BIGSERIAL PRIMARY KEY,
    operation_id       VARCHAR(128) NOT NULL,
    correlation_id     VARCHAR(128),
    operation_type     VARCHAR(32)  NOT NULL, -- DEPOSIT | WITHDRAWAL | TRANSFER | CONVERSION

    source_account     VARCHAR(64),
    target_account     VARCHAR(64),

    amount             NUMERIC(19,2) NOT NULL,
    source_currency    VARCHAR(10)   NOT NULL,
    target_currency    VARCHAR(10),
    fx_rate            NUMERIC(19,8),

    status             VARCHAR(32)   NOT NULL DEFAULT 'NEW', -- NEW | PROCESSED | FAILED
    payload_json       TEXT,

    occurred_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    processed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_operations_operation_id UNIQUE (operation_id),

    CONSTRAINT ck_operations_amount_positive
    CHECK (amount > 0),

    CONSTRAINT ck_operations_type
    CHECK (operation_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CONVERSION')),

    CONSTRAINT ck_operations_conversion_fields
    CHECK (
              operation_type <> 'CONVERSION'
              OR (target_currency IS NOT NULL AND fx_rate IS NOT NULL AND fx_rate > 0)
    )
    );

CREATE INDEX IF NOT EXISTS idx_operations_type
    ON operations (operation_type);

CREATE INDEX IF NOT EXISTS idx_operations_status
    ON operations (status);

CREATE INDEX IF NOT EXISTS idx_operations_created_at
    ON operations (created_at);

CREATE INDEX IF NOT EXISTS idx_operations_correlation_id
    ON operations (correlation_id);