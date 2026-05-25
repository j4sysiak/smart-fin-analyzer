package pl.edu.praktyki.contract.egress

import jakarta.persistence.*
import java.time.Instant

/**
 * Utrwalony log każdej NOWEJ decyzji egress (replay-e nie są zapisywane ponownie).
 * Działa jak append-only audit trail warstwy Egress.
 */
@Entity
@Table(name = "decision_log")
class DecisionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id

    @Column(name = "transaction_id", nullable = false, length = 128)
    String transactionId

    @Column(name = "correlation_id", length = 128)
    String correlationId

    // ACCEPT / ACCEPT_WITH_WARNING / REJECT
    @Column(name = "decision", nullable = false, length = 64)
    String decision

    @Column(name = "reason", length = 512)
    String reason

    @Column(name = "decided_at", nullable = false)
    Instant decidedAt

    // kiedy wpis trafił do decision_log (czas egress)
    @Column(name = "logged_at", nullable = false, updatable = false)
    Instant loggedAt

    @PrePersist
    void prePersist() {
        if (loggedAt == null) {
            loggedAt = Instant.now()
        }
    }
}