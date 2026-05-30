package pl.edu.praktyki.contract.egress.outbox

import jakarta.persistence.*
import java.time.Instant


// ta klasa reprezentuje encję JPA, która jest mapowana na tabelę "egress_outbox" w bazie danych.
@Entity
@Table(
        name = "egress_outbox",
        indexes = [
                @Index(name = "idx_egress_outbox_status_next_attempt", columnList = "status,next_attempt_at"),
                @Index(name = "idx_egress_outbox_correlation_id", columnList = "correlation_id")
        ]
)
class EgressOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id

    @Column(name = "event_id", nullable = false, updatable = false, length = 36, unique = true)
    String eventId

    @Column(name = "event_type", nullable = false, length = 128)
    String eventType

    @Column(name = "transaction_id", nullable = false, length = 128)
    String transactionId

    @Column(name = "correlation_id", length = 128)
    String correlationId

    @Lob
    @Column(name = "payload_json", nullable = false)
    String payloadJson

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    EgressOutboxStatus status

    @Column(name = "attempt_count", nullable = false)
    Integer attemptCount = 0

    @Column(name = "next_attempt_at", nullable = false)
    Instant nextAttemptAt

    @Column(name = "last_error", length = 1024)
    String lastError

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt

    @Column(name = "processed_at")
    Instant processedAt

    @PrePersist
    void prePersist() {
        Instant now = Instant.now()
        if (createdAt == null) createdAt = now
        if (updatedAt == null) updatedAt = now
        if (nextAttemptAt == null) nextAttemptAt = now
        if (status == null) status = EgressOutboxStatus.NEW
        if (attemptCount == null) attemptCount = 0
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now()
    }
}