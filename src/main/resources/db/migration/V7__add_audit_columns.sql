ALTER TABLE transactions
ADD COLUMN created_date TIMESTAMP,
ADD COLUMN last_modified_date TIMESTAMP,
ADD COLUMN created_by VARCHAR(50),
ADD COLUMN last_modified_by VARCHAR(50);