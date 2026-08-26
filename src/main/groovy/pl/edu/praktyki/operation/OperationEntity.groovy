package pl.edu.praktyki.operation

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "operations")
class OperationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id

    // Unikalny ID operacji z zewnętrznego systemu (np. "OP-001")
    @Column(name = "operation_id", nullable = false, unique = true)
    String operationId

    // Do łączenia powiązanych operacji (np. z tej samej paczki wsadowej)
    @Column(name = "correlation_id")
    String correlationId

    // Typ: DEPOSIT | WITHDRAWAL | TRANSFER | CONVERSION
    @Column(name = "operation_type", nullable = false)
    String operationType

    @Column(name = "source_account")
    String sourceAccount

    @Column(name = "target_account")
    String targetAccount

    @Column(name = "amount", nullable = false)
    BigDecimal amount

    @Column(name = "source_currency", nullable = false)
    String sourceCurrency

    @Column(name = "target_currency")
    String targetCurrency

    @Column(name = "fx_rate")
    BigDecimal fxRate

    // Status: NEW | PROCESSED | FAILED
    @Column(name = "status", nullable = false)
    String status = "NEW"

    // Surowy JSON z MockServera (do diagnostyki)
    @Column(name = "payload_json", columnDefinition = "TEXT")
    String payloadJson

    @Column(name = "occurred_at")
    OffsetDateTime occurredAt

    @Column(name = "processed_at")
    OffsetDateTime processedAt

    @Column(name = "created_at")
    OffsetDateTime createdAt = OffsetDateTime.now()

    @Column(name = "updated_at")
    OffsetDateTime updatedAt = OffsetDateTime.now()

    OperationEntity() {}

    @PrePersist
    void onCreate() {
        if (!occurredAt) {
            occurredAt = OffsetDateTime.now()
        }
        if (!status) {
            status = "NEW"
        }
        createdAt = OffsetDateTime.now()
        updatedAt = OffsetDateTime.now()
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now()
    }

    @Override
    String toString() {
        "OperationEntity[id=$operationId, type=$operationType, amount=$amount $sourceCurrency, status=$status]"
    }
}