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

// ten test sprawdza, czy po nieudanej próbie przetworzenia rekordu z tabeli outboxa (np. z powodu uszkodzonego payloadu),
// jego status jest ustawiany na RETRY, a nextAttemptAt jest ustawiany na czas w przyszłości (co oznacza, że będzie ponawiana próba dostarczenia tego payloadu po upływie tego czasu).
// Co pokrywa ten test:
// -- błąd payloadu -> status RETRY, lastError ustawiony, nextAttemptAt w przyszłości, metryka retry++
class EgressOutboxRetrySpec extends BaseIntegrationSpec {

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

    private static void assertDelayWithin(Instant reference, Instant actual, long expectedDelayMs, long toleranceMs) {
        def actualDelay = actual.toEpochMilli() - reference.toEpochMilli()
        assert actualDelay >= expectedDelayMs - toleranceMs
        assert actualDelay <= expectedDelayMs + toleranceMs
    }


    def "błąd przetwarzania powinien ustawić RETRY i nextAttemptAt w przyszłości"() {
        given: "rekord już claimed przez dispatcher (PROCESSING), ale payload jest uszkodzony"
        Instant before = Instant.now()
        def row = saveOutboxRow(
                eventId: "evt-retry-001",
                eventType: "TransactionDecisionEvent",
                transactionId: "TX-RETRY-001",
                correlationId: "CORR-RETRY-001",
                payloadJson: "{invalid-json",
                status: EgressOutboxStatus.PROCESSING,
                attemptCount: 1,
                nextAttemptAt: before
        )

        when:
        processor.process(row.id)

        then:
        def updated = outboxRepository.findById(row.id).orElse(null)
        updated != null
        updated.status == EgressOutboxStatus.RETRY
        updated.lastError != null
        updated.processedAt == null

        and: "backoff po 1. nieudanej próbie to ok. 2 sekundy z tolerancją"
        assertDelayWithin(before, updated.nextAttemptAt, 2000L, 700L)

        and: "metryka retry została zwiększona"
        def retryCounter = meterRegistry.find("egress.outbox.dispatch.retry.count").counter()
        retryCounter != null
        retryCounter.count() >= 1.0d
    }
}

