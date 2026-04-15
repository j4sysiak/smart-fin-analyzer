-- Indeks na kategorię - drastycznie przyspieszy filtrowanie
CREATE INDEX idx_transactions_category ON transactions(category);

-- Indeks na datę - przyspieszy raporty czasowe
CREATE INDEX idx_transactions_date ON transactions(date);