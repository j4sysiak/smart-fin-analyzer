CREATE TABLE financial_summary (
                                   id VARCHAR(255) PRIMARY KEY,
                                   total_balance DECIMAL(19, 2) DEFAULT 0.0,
                                   transaction_count BIGINT DEFAULT 0
);

INSERT INTO financial_summary (id, total_balance, transaction_count) VALUES ('GLOBAL', 0.0, 0);