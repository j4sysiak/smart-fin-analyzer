package pl.edu.praktyki.contract.egress.outbox

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.contract.egress.DecisionLogRepository
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyRepository

import java.time.Instant

@ActiveProfiles("tc")
@TestPropertySource(properties = [
        "app.egress.outbox.enabled=false"
])

// ten test sprawdza, czy po przekroczeniu maksymalnej liczby prób (maxAttempts)
// rekord z tabeli outboxa jest oznaczany jako DEAD, a nie jako RETRY.
// Pokrycie tego przypadku jest ważne, bo gdy payload jest uszkodzony (np. niepoprawny JSON),
// to nie ma sensu próbować go ponownie dostarczać.
// Co pokrywa ten test:
// -- po osiągnięciu limitu prób -> status DEAD, metryka dead++

class EgressOutboxDeadLetterSpec extends BaseIntegrationSpec {

    @Autowired EgressOutboxRepository outboxRepository
    @Autowired EgressOutboxProcessor processor
    @Autowired MeterRegistry meterRegistry
    @Autowired DecisionLogRepository decisionLogRepository
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository

    def setup() {
        outboxRepository.deleteAll()
        decisionLogRepository.deleteAll()
        idempotencyKeyRepository.deleteAll()
        clearMetrics()
    }

    private void clearMetrics() {
        meterRegistry.meters.toList().each { meterRegistry.remove(it) }
    }

    private EgressOutboxEntity saveOutboxRow(Map args) {
        outboxRepository.save(new EgressOutboxEntity(args))
    }

    def "po przekroczeniu maxAttempts rekord powinien trafić do DEAD"() {
        given: "rekord PROCESSING z attemptCount=7 (domyślny max-attempts)"
        def row = saveOutboxRow(
                eventId: "evt-dead-001",
                eventType: "TransactionDecisionEvent",
                transactionId: "TX-DEAD-001",
                correlationId: "CORR-DEAD-001",
                payloadJson: "{invalid-json",
                status: EgressOutboxStatus.PROCESSING,
                attemptCount: 7,
                nextAttemptAt: Instant.now()
        )

        when:
        processor.process(row.id)

        then:
        def updated = outboxRepository.findById(row.id).orElse(null)
        updated != null
        updated.status == EgressOutboxStatus.DEAD
        updated.lastError != null
        updated.processedAt == null

        and: "metryka dead letter została zwiększona"
        def deadCounter = meterRegistry.find("egress.outbox.dispatch.dead.count").counter()
        deadCounter != null
        deadCounter.count() >= 1.0d
    }
}

