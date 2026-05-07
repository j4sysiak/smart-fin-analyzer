-- Lab89 – Spring Batch
-- Tabela do przechowywania wyników miesięcznych rozliczeń finansowych.
-- Jeden wiersz = jedna przetworzona transakcja (mapowanie 1:1 z Procesora).
-- Dla zagregowanego widoku użyj: SELECT settlement_month, category, SUM(total_amount), SUM(transaction_count) GROUP BY 1,2
CREATE TABLE IF NOT EXISTS monthly_settlement (
    id                BIGSERIAL     PRIMARY KEY,
    settlement_month  VARCHAR(7)    NOT NULL,   -- "yyyy-MM", np. "2025-04"
    category          VARCHAR(255)  NOT NULL,
    total_amount      NUMERIC(19,2) NOT NULL,
    transaction_count INTEGER       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_monthly_settlement_month_cat
    ON monthly_settlement (settlement_month, category);

