-- Soft delete (enterprise) for transactions
ALTER TABLE transactions
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL;

-- Envers audit table must store soft-delete state too
ALTER TABLE transactions_aud
    ADD COLUMN deleted BOOLEAN,
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by VARCHAR(100);