-- 1. Tworzymy sekwencję z krokiem 50 (zoptymalizowana pod Twojego BulkSavera i Hibernate)
CREATE SEQUENCE tx_seq START WITH 1 INCREMENT BY 50;

-- 2. Tworzenie głównej tabeli transakcji
CREATE TABLE transactions (
                              db_id BIGINT PRIMARY KEY, -- ID będziemy brać z sekwencji tx_seq
                              original_id VARCHAR(255),
                              date DATE,
                              amount DECIMAL(19, 2),
                              currency VARCHAR(10),
                              amountpln DECIMAL(19, 2),
                              category VARCHAR(255),
                              description VARCHAR(255)
);

-- 3. Tworzenie tabeli dla tagów
CREATE TABLE transaction_entity_tags (
                                         transaction_entity_db_id BIGINT NOT NULL,
                                         tags VARCHAR(255),
                                         CONSTRAINT fk_transaction FOREIGN KEY (transaction_entity_db_id) REFERENCES transactions(db_id)
);