package pl.edu.praktyki.contract.egress.outbox

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.contract.egress.DecisionLogRepository
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyRepository

import java.time.Instant
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

@ActiveProfiles("tc")
@TestPropertySource(properties = [
        "app.egress.outbox.enabled=true",
        "app.egress.outbox.base-delay-ms=500",
        "app.egress.outbox.max-attempts=4",
        "app.egress.outbox.poll-ms=100"
])

/**
 * E2E testy dla kompletnego cyklu życia rekordu outboxa (egress)
 * poprzez bezpośrednie wywołanie dispatcher.dispatch()
 */
class EgressOutboxDispatcherE2ESpec extends BaseIntegrationSpec {

    @Autowired EgressOutboxRepository outboxRepository
    @Autowired EgressOutboxDispatcher dispatcher
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

    private void dispatchOutbox() {
        dispatcher.dispatch()
    }

    private void dispatchAndAwaitSent(Long outboxId) {
        dispatchOutbox()
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            assert outboxRepository.findById(outboxId).orElseThrow().status == EgressOutboxStatus.SENT
        }
    }

    private void clearMetrics() {
        meterRegistry.meters.toList().each { meterRegistry.remove(it) }
    }

    private EgressOutboxEntity saveOutboxRow(Map args) {
        outboxRepository.save(new EgressOutboxEntity(args))
    }

    def "powinien przeprowadzić rekord przez NEW -> PROCESSING -> SENT przy ręcznym wywołaniu dispatcher.dispatch()"() {
        given: "świeży rekord w statusie NEW (poprawny payload)"
        Instant createdAt = Instant.now()
        def row = saveOutboxRow(
                eventId: "evt-e2e-001",
                eventType: "TransactionDecisionEvent",
                transactionId: "TX-E2E-001",
                correlationId: "CORR-E2E-001",
                payloadJson: '{"correlationId":"CORR-E2E-001","transactionId":"TX-E2E-001","decision":"ACCEPT","reason":"E2E test","decidedAt":"2026-05-25T18:00:00Z"}',
                status: EgressOutboxStatus.NEW,
                attemptCount: 0,
                nextAttemptAt: Instant.now(),
                createdAt: createdAt
        )

        when: "manualne wywołanie dispatcher.dispatch()"
        dispatchAndAwaitSent(row.id)

        then: "po dispatch + synchroniczny processor.process(), status powinien być SENT"
        def result = outboxRepository.findById(row.id).orElseThrow()
        result.status == EgressOutboxStatus.SENT
        result.processedAt != null
        result.lastError == null
        result.attemptCount == 1

        and: "metryka success.count powinna być zwiększona"
        def successCounter = meterRegistry.find("egress.outbox.dispatch.success.count")?.counter()
        successCounter?.count() >= 1.0d
    }

    def "powinien retry wielokrotnie aż do DEAD, z asercją dokładnych backoff czasów"() {
        given: "rekord w statusie NEW z uszkodzonym payloadem (nie będzie mógł być dostarczony)"
        Instant initialTime = Instant.now()
        def row = saveOutboxRow(
                eventId: "evt-e2e-retry-001",
                eventType: "TransactionDecisionEvent",
                transactionId: "TX-E2E-RETRY-001",
                correlationId: "CORR-E2E-RETRY-001",
                payloadJson: "{invalid-json-corrupt",  // ← spowoduje błąd przy deserializacji
                status: EgressOutboxStatus.NEW,
                attemptCount: 0,
                nextAttemptAt: initialTime,
                createdAt: initialTime
        )

        and: "wstępne czyszczenie metryk"
        clearMetrics()

        when: "1. próba: dispatcher.dispatch() -> processor.process()"
        dispatchOutbox()

        then: "1. próba: status RETRY z nextAttemptAt = now + 500ms (base-delay-ms)"
        def retry1 = outboxRepository.findById(row.id).orElseThrow()
        retry1.status == EgressOutboxStatus.RETRY
        retry1.attemptCount == 1
        retry1.lastError != null
        def retry1Time = retry1.nextAttemptAt
        retry1Time.isAfter(initialTime.plusMillis(400))
        retry1Time.isBefore(initialTime.plusMillis(700))

        and: "metryka retry.count = 1"
        await().atMost(2, TimeUnit.SECONDS).until {
            meterRegistry.find("egress.outbox.dispatch.retry.count")?.counter()?.count() ?: 0.0d > 0.0d
        }

        when: "2. próba: czekamy i wołamy dispatch ponownie"
        Thread.sleep(600)
        dispatchOutbox()

        then: "2. próba: status RETRY z nextAttemptAt = now + 1000ms (exponential backoff: 500 * 2^1)"
        def retry2 = outboxRepository.findById(row.id).orElseThrow()
        retry2.status == EgressOutboxStatus.RETRY
        retry2.attemptCount == 2
        retry2.lastError != null
        def retry2Time = retry2.nextAttemptAt
        def retry1EndMs = retry1Time.toEpochMilli()
        def expectedMinRetry2 = retry1EndMs + 900
        def expectedMaxRetry2 = retry1EndMs + 1200
        retry2Time.toEpochMilli() > expectedMinRetry2
        retry2Time.toEpochMilli() < expectedMaxRetry2

        when: "3. próba: czekamy i wołamy dispatch ponownie"
        Thread.sleep(1100)
        dispatchOutbox()

        then: "3. próba: status RETRY z nextAttemptAt = now + 2000ms (exponential backoff: 500 * 2^2)"
        def retry3 = outboxRepository.findById(row.id).orElseThrow()
        retry3.status == EgressOutboxStatus.RETRY
        retry3.attemptCount == 3
        def retry3Time = retry3.nextAttemptAt
        def retry2EndMs = retry2Time.toEpochMilli()
        def expectedMinRetry3 = retry2EndMs + 1800
        def expectedMaxRetry3 = retry2EndMs + 2300
        retry3Time.toEpochMilli() > expectedMinRetry3
        retry3Time.toEpochMilli() < expectedMaxRetry3

        when: "4. próba (ostatnia, max-attempts=4): ostatni retry vs DEAD"
        Thread.sleep(2100)
        dispatchOutbox()

        then: "4. próba: status zmienić się na DEAD (osiągnięto maxAttempts)"
        def dead = outboxRepository.findById(row.id).orElseThrow()
        dead.status == EgressOutboxStatus.DEAD
        dead.attemptCount == 4
        dead.lastError != null
        dead.processedAt == null

        and: "metryka dead.count powinna być zwiększona"
        def deadCounter = meterRegistry.find("egress.outbox.dispatch.dead.count")?.counter()
        deadCounter?.count() >= 1.0d

        and: "sumaryczne retryki: 3 (bo na 4. próbie następuje DEAD)"
        def retryCounter = meterRegistry.find("egress.outbox.dispatch.retry.count")?.counter()
        retryCounter?.count() >= 3.0d
    }
}
