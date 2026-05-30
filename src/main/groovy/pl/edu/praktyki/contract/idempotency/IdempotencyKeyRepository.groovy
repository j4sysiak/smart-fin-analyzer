package pl.edu.praktyki.contract.idempotency

import org.springframework.data.jpa.repository.JpaRepository

// Ten interfejs jest rozszerzeniem `Spring Data JPA Repository`,
// który umożliwia wykonywanie operacji CRUD na tabeli "idempotency_keys" w bazie danych.
// Dodatkowo definiuje metodę do wyszukiwania encji IdempotencyKeyEntity na podstawie correlationId,
// co jest kluczowe dla implementacji idempotencji w systemie. Dzięki temu można sprawdzić,
// czy dla danego correlationId już istnieje zapis, co pozwala uniknąć ponownego przetwarzania tej samej operacji.

interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

    Optional<IdempotencyKeyEntity> findByCorrelationId(String correlationId)
}