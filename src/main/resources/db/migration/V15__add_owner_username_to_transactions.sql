-- 1. Dodajemy kolumnę do głównej tabeli
ALTER TABLE transactions
    ADD COLUMN owner_username VARCHAR(50);

-- 2. Indeks dla wydajności zapytań "per user"
CREATE INDEX IF NOT EXISTS idx_transactions_owner_username
    ON transactions(owner_username);

-- Dla istniejących rekordów ustawiamy właściciela technicznego,
-- żeby migracja nie zostawiła pustych danych historycznych - (żeby NOT NULL nie wybuchł później).
UPDATE transactions
SET owner_username = COALESCE(created_by, 'admin')
WHERE owner_username IS NULL;

-- 4. Dodajemy kolumnę do tabeli audytowej Enversa
ALTER TABLE transactions_aud
    ADD COLUMN owner_username VARCHAR(50);