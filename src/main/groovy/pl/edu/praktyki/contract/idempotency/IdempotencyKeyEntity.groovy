package pl.edu.praktyki.contract.idempotency

import jakarta.persistence.*

import java.time.Instant

// Ta klasa reprezentuje encję JPA dla tabeli "idempotency_keys" w bazie danych.
@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = [
                @UniqueConstraint(
                        name = "ux_idempotency_keys_correlation_id",
                        columnNames = ["correlation_id"]
                )
        ]
)
class IdempotencyKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 128)
    String correlationId

    @Column(name = "transaction_id", nullable = false, length = 128)
    String transactionId

    @Column(name = "decision", nullable = false, length = 64)
    String decision

    @Column(name = "reason", length = 512)
    String reason

    @Column(name = "decided_at", nullable = false)
    Instant decidedAt

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now()
        }
    }
}