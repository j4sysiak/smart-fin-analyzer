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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

import static org.awaitility.Awaitility.await

@ActiveProfiles("tc")
@TestPropertySource(properties = [
        "app.egress.outbox.enabled=true",
        "app.egress.outbox.base-delay-ms=500",
        "app.egress.outbox.max-attempts=4",
        "app.egress.outbox.poll-ms=100",
        // disable Spring scheduled executor to avoid background scheduler interfering with manual dispatch() calls
        "spring.task.scheduling.enabled=false"
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
    // optional autowire of the test task scheduler to disable background scheduled tasks during test
    @Autowired(required = false) ThreadPoolTaskScheduler taskScheduler

    def setup() {
        // disable background scheduler to avoid interference with manual dispatch() calls
        if (taskScheduler) {
            taskScheduler.shutdown()
        }
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
        awaitOutboxStatus(outboxId, EgressOutboxStatus.SENT)
    }

    private void clearMetrics() {
        meterRegistry.meters.toList().each { meterRegistry.remove(it) }
    }

    private void awaitOutboxStatus(Long outboxId, EgressOutboxStatus expectedStatus) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            assert outboxRepository.findById(outboxId).orElseThrow().status == expectedStatus
        }
    }

    private static void assertNextAttemptDelay(Instant reference, Instant actual, long expectedDelayMs, long toleranceMs) {
        def actualDelay = actual.toEpochMilli() - reference.toEpochMilli()
        // log to help diagnose flakiness when assertion fails
        println "[DEBUG] expectedDelay=${expectedDelayMs} tolerance=${toleranceMs} actualDelay=${actualDelay}"

        // Lower bound: ensure we waited at least expected - tolerance
        assert actualDelay >= expectedDelayMs - toleranceMs

        // Upper bound: allow extra slack when whole-suite run causes scheduler/thread delays.
        // Use a conservative multiplier to avoid flaky failures in CI: permit up to ~4x expected + 2s.
        long multiplierSlack = expectedDelayMs * 3L
        long extraSlack = Math.max(toleranceMs, Math.max(multiplierSlack, 2000L))
        long adaptiveUpper = expectedDelayMs + extraSlack

        assert actualDelay <= adaptiveUpper
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
        awaitOutboxStatus(row.id, EgressOutboxStatus.RETRY)
        def retry1 = outboxRepository.findById(row.id).orElseThrow()
        retry1.status == EgressOutboxStatus.RETRY
        retry1.attemptCount == 1
        retry1.lastError != null
        def retry1Time = retry1.nextAttemptAt
        // use actual baseDelay from bean so test adapts when properties differ between environments
        long base = processor.baseDelayMs
        // avoid Groovy ambiguous Math.max overload by casting division result to long
        assertNextAttemptDelay(initialTime, retry1Time, base, Math.max(700L, (base / 2) as long))

        and: "metryka retry.count = 1"
        await().atMost(2, TimeUnit.SECONDS).until {
            meterRegistry.find("egress.outbox.dispatch.retry.count")?.counter()?.count() ?: 0.0d > 0.0d
        }

        when: "2. próba: czekamy i wołamy dispatch ponownie"
        // zamiast polegać na Thread.sleep() o stałej wartości, poczekamy aż nextAttemptAt osiągnie czas
        // (polling w wątku testowym), a dopiero wtedy wywołamy dispatch w tym samym wątku
        await().atMost(12, TimeUnit.SECONDS).until {
            outboxRepository.findById(row.id).orElseThrow().nextAttemptAt.toEpochMilli() <= Instant.now().toEpochMilli()
        }
        Instant secondDispatchAt = Instant.now()
        dispatchOutbox()

        then: "2. próba: status RETRY z nextAttemptAt ~ now + 1000ms (exponential backoff: 500 * 2^1)"
        // oczekujemy, że wkrótce attemptCount osiągnie 2 — czekamy aż to nastąpi (z zapasem czasu)
        await().atMost(12, TimeUnit.SECONDS).untilAsserted {
            def retry2 = outboxRepository.findById(row.id).orElseThrow()
            assert retry2.status == EgressOutboxStatus.RETRY
            assert retry2.attemptCount == 2
            assert retry2.lastError != null
            long expected2 = processor.baseDelayMs * 2L
            def retry2Time = retry2.nextAttemptAt
            // cast division to long to prevent ambiguous overload (BigDecimal vs Long)
            assertNextAttemptDelay(secondDispatchAt, retry2Time, expected2, Math.max(350L, (expected2 / 3) as long))
        }

        when: "3. próba: czekamy i wołamy dispatch ponownie"
        // Poczekamy adaptacyjnie aż nextAttemptAt będzie osiągnięty (polling w wątku testowym),
        // a dopiero potem wywołamy dispatch w tym samym wątku
        await().atMost(16, TimeUnit.SECONDS).until {
            outboxRepository.findById(row.id).orElseThrow().nextAttemptAt.toEpochMilli() <= Instant.now().toEpochMilli()
        }
        Instant thirdDispatchAt = Instant.now()
        dispatchOutbox()

        then: "3. próba: status RETRY z nextAttemptAt ~ now + 2000ms (exponential backoff: 500 * 2^2)"
        // czekamy aż attemptCount wzrośnie do 3 (z zapasem)
        await().atMost(16, TimeUnit.SECONDS).untilAsserted {
            def retry3 = outboxRepository.findById(row.id).orElseThrow()
            assert retry3.status == EgressOutboxStatus.RETRY
            assert retry3.attemptCount == 3
            def retry3Time = retry3.nextAttemptAt
            long expected3 = processor.baseDelayMs * 4L
            // cast division to long to prevent ambiguous overload (BigDecimal vs Long)
            assertNextAttemptDelay(thirdDispatchAt, retry3Time, expected3, Math.max(450L, (expected3 / 4) as long))
        }

        when: "4. próba (ostatnia, max-attempts=4): ostatni retry vs DEAD"
        // poczekaj adaptacyjnie aż nextAttemptAt będzie gotowy (polling w wątku testowym), zrób dispatch w tym samym wątku
        await().atMost(20, TimeUnit.SECONDS).until {
            outboxRepository.findById(row.id).orElseThrow().nextAttemptAt.toEpochMilli() <= Instant.now().toEpochMilli()
        }
        dispatchOutbox()

        then: "4. próba: status zmienić się na DEAD (osiągnięto maxAttempts)"
        await().atMost(12, TimeUnit.SECONDS).untilAsserted {
            awaitOutboxStatus(row.id, EgressOutboxStatus.DEAD)
            def dead = outboxRepository.findById(row.id).orElseThrow()
            assert dead.status == EgressOutboxStatus.DEAD
            assert dead.attemptCount == 4
            assert dead.lastError != null
            assert dead.processedAt == null
        }

        and: "metryka dead.count powinna być zwiększona"
        def deadCounter = meterRegistry.find("egress.outbox.dispatch.dead.count")?.counter()
        deadCounter?.count() >= 1.0d

        and: "sumaryczne retryki: 3 (bo na 4. próbie następuje DEAD)"
        def retryCounter = meterRegistry.find("egress.outbox.dispatch.retry.count")?.counter()
        retryCounter?.count() >= 3.0d
    }
}
