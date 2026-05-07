CREATE TABLE categories_aud (
id BIGINT NOT NULL,
rev INTEGER NOT NULL,
revtype SMALLINT,
name VARCHAR(50),
monthly_limit DECIMAL(19, 2),
PRIMARY KEY (id, rev),
CONSTRAINT fk_categories_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo(rev)
);