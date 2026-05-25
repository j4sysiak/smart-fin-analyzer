package pl.edu.praktyki.contract.idempotency

import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {
    Optional<IdempotencyKeyEntity> findByCorrelationId(String correlationId)
}