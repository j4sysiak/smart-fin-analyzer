-- Tworzenie głównej tabeli transakcji
CREATE TABLE transactions (
                              db_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              original_id VARCHAR(255),
                              date DATE,
                              amount DECIMAL(19, 2),
                              currency VARCHAR(10),
                              amountpln DECIMAL(19, 2),
                              category VARCHAR(255),
                              description VARCHAR(255)
);

-- Tworzenie tabeli powiązanej dla tagów (1 transakcja -> wiele tagów)
CREATE TABLE transaction_entity_tags (
                                         transaction_entity_db_id BIGINT NOT NULL,
                                         tags VARCHAR(255),
                                         FOREIGN KEY (transaction_entity_db_id) REFERENCES transactions(db_id)
);