package pl.edu.praktyki.contract

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.contract.egress.DecisionLogRepository
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyRepository
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import java.time.Instant

@ActiveProfiles("tc")
class TransactionDecisionEgressSpec extends BaseIntegrationSpec {

    @Autowired TransactionAnalysisOrchestrator orchestrator
    @Autowired DecisionLogRepository           decisionLogRepository
    @Autowired IdempotencyKeyRepository        idempotencyKeyRepository

    def setup() {
        decisionLogRepository.deleteAll()
        idempotencyKeyRepository.deleteAll()
    }

    // =========================================================================
    // Scenariusz 1: Nowa decyzja → 1 wpis w decision_log
    // =========================================================================
    def "nowa decyzja powinna trafić do decision_log dokładnie raz"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-EGRESS-001")
                .accountId("ACC-EGRESS-001")
                .correlationId("CORR-EGRESS-001")
                .timestamp(Instant.parse("2026-05-25T10:00:00Z"))
                .amount(9999.99)   // → ACCEPT
                .payload([:])
                .build()

        when:
        def decision = orchestrator.process(request)

        then: "decision_log ma dokładnie 1 rekord"
        decisionLogRepository.countByCorrelationId("CORR-EGRESS-001") == 1

        and: "rekord w decision_log zgadza się z decyzją"
        def logged = decisionLogRepository.findFirstByCorrelationIdOrderByLoggedAtAsc("CORR-EGRESS-001").orElse(null)
        logged != null
        logged.transactionId == decision.transactionId
        logged.correlationId == decision.correlationId
        logged.decision      == decision.decision
        logged.reason        == decision.reason
        logged.loggedAt      != null
    }

    // =========================================================================
    // Scenariusz 2: Replay (ten sam correlationId drugi raz) → decision_log NIE rośnie
    // =========================================================================
    def "replay nie powinien dodawać kolejnego wpisu do decision_log"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-EGRESS-002")
                .accountId("ACC-EGRESS-002")
                .correlationId("CORR-EGRESS-002")
                .timestamp(Instant.parse("2026-05-25T10:01:00Z"))
                .amount(9999.99)
                .payload([:])
                .build()

        when: "pierwszy request — nowa decyzja"
        orchestrator.process(request)

        and: "drugi request — ten sam correlationId (replay z innym danymi)"
        def replayRequest = TransactionIngressRequest.builder()
                .transactionId("TX-EGRESS-002-B")   // inne TX-ID
                .accountId("ACC-EGRESS-002")
                .correlationId("CORR-EGRESS-002")    // TEN SAM correlationId!
                .timestamp(Instant.parse("2026-05-25T10:02:00Z"))
                .amount(50000.00)                     // inne amount (normalnie FLAGGED)
                .payload([retry: true])
                .build()
        orchestrator.process(replayRequest)

        then: "decision_log nadal ma dokładnie 1 rekord (replay nie duplikuje)"
        decisionLogRepository.countByCorrelationId("CORR-EGRESS-002") == 1

        and: "rekord pochodzi z PIERWSZEGO requestu"
        def logged = decisionLogRepository
                .findFirstByCorrelationIdOrderByLoggedAtAsc("CORR-EGRESS-002")
                .orElse(null)
        logged.transactionId == "TX-EGRESS-002"     // TX-ID z pierwszego requestu
        logged.decision      == "ACCEPT"             // wynik pierwszego requestu
    }

    // =========================================================================
    // Scenariusz 3: Brak correlationId (legacy) → też trafia do decision_log
    // =========================================================================
    def "decyzja bez correlationId powinna trafić do decision_log"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-EGRESS-003")
                .accountId("ACC-EGRESS-003")
                .correlationId("   ")   // pusty → legacy path
                .timestamp(Instant.parse("2026-05-25T10:03:00Z"))
                .amount(500.00)
                .payload([:])
                .build()

        when:
        orchestrator.process(request)

        then: "decision_log ma 1 rekord (correlationId jest null)"
        decisionLogRepository.findAll().size() == 1
        decisionLogRepository.findAll()[0].transactionId == "TX-EGRESS-003"
    }

    // =========================================================================
    // Scenariusz 4: Race condition Egress — 15 równoległych requestów
    // =========================================================================
    def "race: 15 równoległych requestów z tym samym correlationId → 1 wpis decision_log"() {
        given:
        String correlationId = "CORR-EGRESS-RACE-015"
        int threadCount = 15

        def pool = Executors.newFixedThreadPool(threadCount)
        def ready = new CountDownLatch(threadCount)
        def start = new CountDownLatch(1)
        def errors = new CopyOnWriteArrayList<Throwable>()

        when: "15 wątków startuje równocześnie z tym samym correlationId"
        def futures = (1..threadCount).collect { idx ->
            pool.submit({
                ready.countDown()
                start.await(10, TimeUnit.SECONDS)

                def req = TransactionIngressRequest.builder()
                        .transactionId("TX-EGRESS-RACE-${String.format('%03d', idx)}")
                        .accountId("ACC-EGRESS-RACE")
                        .correlationId(correlationId)
                        .timestamp(Instant.parse("2026-05-25T11:00:00Z"))
                        .amount(9999.99)  // → ACCEPT
                        .payload([thread: idx])
                        .build()

                try {
                    orchestrator.process(req)
                } catch (Throwable t) {
                    errors.add(t)
                }
            } as Callable<Void>)
        }

        assert ready.await(10, TimeUnit.SECONDS) : "wątki nie zebrały się na czas"
        start.countDown()

        futures.each { f -> f.get(10, TimeUnit.SECONDS) }

        then: "brak wyjątków"
        errors.isEmpty()

        and: "idempotency_keys ma 1 rekord"
        idempotencyKeyRepository.count() == 1

        and: "decision_log ma DOKŁADNIE 1 rekord (bez duplikatów!)"
        decisionLogRepository.countByCorrelationId(correlationId) == 1

        and: "wszystkie 15 requestów dostało tę samą decyzję"
        def logged = decisionLogRepository
                .findFirstByCorrelationIdOrderByLoggedAtAsc(correlationId)
                .orElse(null)
        logged != null
        logged.decision == "ACCEPT"

        cleanup:
        pool.shutdownNow()
    }
}