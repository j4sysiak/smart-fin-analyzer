package pl.edu.praktyki.contract

import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyEntity
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyRepository
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class TransactionAnalysisOrchestratorIdempotencySpec extends Specification {

    TransactionAnalyzer analyzer = Mock()
    TransactionDecisionPolicy decisionPolicy = new DefaultTransactionDecisionPolicy()
    IdempotencyKeyRepository idempotencyKeyRepository = Mock()
    ApplicationEventPublisher eventPublisher = Stub(ApplicationEventPublisher)

    TransactionAnalysisOrchestrator orchestrator

    private final AtomicInteger analyzeCallsCounter = new AtomicInteger(0)

    def setup() {
        orchestrator = new TransactionAnalysisOrchestrator(
                analyzer,
                decisionPolicy,
                idempotencyKeyRepository,
                eventPublisher
        )
        analyzeCallsCounter.set(0)
    }

    def "powinien zwrócić wynik idempotentny przy tym samym correlationId (analyze tylko raz)"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-IDEM-001")
                .accountId("ACC-IDEM-001")
                .correlationId("CORR-IDEM-SAME")
                .timestamp(Instant.parse("2026-05-23T18:00:00Z"))
                .amount(9999.99)
                .payload([:])
                .build()

        and: "repo udaje brak wpisu przy pierwszym odczycie, a potem zwraca zapisany rekord"
        def persisted = new IdempotencyKeyEntity(
                correlationId: "CORR-IDEM-SAME",
                transactionId: "TX-IDEM-001",
                decision: "ACCEPT",
                reason: "Status OK - transaction accepted",
                decidedAt: Instant.parse("2026-05-23T18:00:10Z")
        )

        idempotencyKeyRepository.findByCorrelationId("CORR-IDEM-SAME") >>> [
                Optional.empty(),
                Optional.of(persisted)
        ]
        idempotencyKeyRepository.saveAndFlush(_ as IdempotencyKeyEntity) >> { IdempotencyKeyEntity e -> e }

        when:
        def first = orchestrator.process(request)
        def second = orchestrator.process(request)

        then:
        1 * analyzer.analyze(_ as TransactionIngressRequest) >> { TransactionIngressRequest req ->
            analyzeCallsCounter.incrementAndGet()
            AnalysisResult.builder()
                    .transactionId(req.transactionId)
                    .correlationId(req.correlationId)
                    .status(AnalysisStatus.OK)
                    .analyzedAt(Instant.parse("2026-05-23T18:00:10Z"))
                    .details("mocked analyzer")
                    .build()
        }

        and:
        first.decision == "ACCEPT"
        second.decision == "ACCEPT"
        first.reason == "Status OK - transaction accepted"
        second.reason == "Status OK - transaction accepted"
        first.transactionId == second.transactionId
        first.correlationId == second.correlationId
        analyzeCallsCounter.get() == 1
    }

    def "powinien pominąć idempotency gdy correlationId jest pusty i wywołać analyze dwa razy"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-IDEM-002")
                .accountId("ACC-IDEM-002")
                .correlationId("   ")
                .timestamp(Instant.parse("2026-05-23T18:10:00Z"))
                .amount(10000.01)
                .payload([:])
                .build()

        when:
        def first = orchestrator.process(request)
        def second = orchestrator.process(request)

        then:
        2 * analyzer.analyze(_ as TransactionIngressRequest) >> { TransactionIngressRequest req ->
            analyzeCallsCounter.incrementAndGet()
            AnalysisResult.builder()
                    .transactionId(req.transactionId)
                    .correlationId(req.correlationId)
                    .status(AnalysisStatus.FLAGGED)
                    .analyzedAt(Instant.parse("2026-05-23T18:10:10Z"))
                    .details("mocked analyzer")
                    .build()
        }

        and: "brak wywołań repo dla pustego correlationId"
        0 * idempotencyKeyRepository._

        and:
        first.decision == "ACCEPT_WITH_WARNING"
        second.decision == "ACCEPT_WITH_WARNING"
        analyzeCallsCounter.get() == 2
    }
}