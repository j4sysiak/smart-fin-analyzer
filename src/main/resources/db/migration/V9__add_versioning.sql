-- Dodajemy kolumnę wersji. Każdy update będzie ją zwiększał o 1.
ALTER TABLE financial_summary ADD COLUMN version BIGINT DEFAULT 0;