-- Tworzy tabelę counters używaną przez encję Counter
-- Składnia kompatybilna z H2 (AUTO_INCREMENT) oraz działa również w wielu innych bazach
CREATE TABLE counters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    counter_value INTEGER
);

