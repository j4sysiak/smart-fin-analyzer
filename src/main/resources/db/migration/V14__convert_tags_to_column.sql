-- V14: Zamiana @ElementCollection (transaction_entity_tags) na zwykłą kolumnę tags w transactions.
-- TagsConverter (JPA AttributeConverter) przechowuje tagi jako CSV string.
-- Korzyść: eliminuje ClassCastException (HHH-17024) przy @Audited + @ElementCollection w Hibernate 6.6.x.

-- 1. Dodaj kolumnę tags do tabeli transactions
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS tags VARCHAR(1000);

-- 2. Migracja danych: sklejamy tagi z transaction_entity_tags w CSV i wstawiamy do transactions.tags
UPDATE transactions t
SET tags = (
    SELECT string_agg(et.tags, ',' ORDER BY et.tags)
    FROM transaction_entity_tags et
    WHERE et.transaction_entity_db_id = t.db_id
)
WHERE EXISTS (
    SELECT 1 FROM transaction_entity_tags et WHERE et.transaction_entity_db_id = t.db_id
);

-- 3. Usuń starą tabelę join (nie jest już potrzebna)
DROP TABLE IF EXISTS transaction_entity_tags;

