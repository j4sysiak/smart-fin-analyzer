package pl.edu.praktyki.contract.egress.outbox

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.contract.egress.DecisionLogRepository
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyRepository

import java.time.Instant

@ActiveProfiles("tc")
@TestPropertySource(properties = [
        "app.egress.outbox.enabled=false"
])

// ten test sprawdza, czy przy próbie dostarczenia dwóch payloadów z tym samym correlationId,
// tylko pierwszy z nich jest zapisywany w tabeli decision_log,
// a drugi jest rozpoznawany jako duplikat i pomijany (nie jest tworzony drugi wpis w decision_log).
// Co pokrywa ten test:
//  -- drugi delivery z tym samym correlationId nie duplikuje decision_log, metryka duplicate.skip++
class EgressOutboxIdempotentDeliverySpec extends BaseIntegrationSpec {

    @Autowired EgressDecisionDeliveryService deliveryService
    @Autowired DecisionLogRepository decisionLogRepository
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository
    @Autowired EgressOutboxRepository outboxRepository
    @Autowired TransactionTemplate transactionTemplate
    @Autowired MeterRegistry meterRegistry

    def setup() {
        outboxRepository.deleteAll()
        decisionLogRepository.deleteAll()
        idempotencyKeyRepository.deleteAll()
        meterRegistry.meters.toList().each { meterRegistry.remove(it) }
    }


    def "drugi delivery tego samego correlationId nie powinien duplikowac decision_log"() {
        given:
        def first = new DecisionEgressPayload(
                transactionId: "TX-IDEMP-001",
                correlationId: "CORR-IDEMP-001",
                decision: "ACCEPT",
                reason: "Status OK - transaction accepted",
                decidedAt: Instant.parse("2026-05-25T13:00:00Z"),
                replay: false,
                occurredAt: Instant.parse("2026-05-25T13:00:00Z")
        )

        def duplicate = new DecisionEgressPayload(
                transactionId: "TX-IDEMP-001-DUP",
                correlationId: "CORR-IDEMP-001",
                decision: "ACCEPT_WITH_WARNING",
                reason: "Should be skipped",
                decidedAt: Instant.parse("2026-05-25T13:00:02Z"),
                replay: false,
                occurredAt: Instant.parse("2026-05-25T13:00:02Z")
        )

        when:
        transactionTemplate.execute { deliveryService.deliver(first) }
        transactionTemplate.execute { deliveryService.deliver(duplicate) }

        then: "w decision_log zostaje tylko pierwszy wpis"
        decisionLogRepository.countByCorrelationId("CORR-IDEMP-001") == 1

        and:
        def saved = decisionLogRepository.findFirstByCorrelationIdOrderByLoggedAtAsc("CORR-IDEMP-001").orElse(null)
        saved != null
        saved.transactionId == "TX-IDEMP-001"
        saved.decision == "ACCEPT"

        and: "metryka duplicate.skip została zwiększona"
        def duplicateSkipCounter = meterRegistry.find("egress.outbox.delivery.duplicate.skip")
                .tag("decision", "ACCEPT_WITH_WARNING")
                .counter()
        duplicateSkipCounter != null
        duplicateSkipCounter.count() >= 1.0d
    }
}

