package pl.edu.praktyki.contract

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

/*
  Przypadki testowe (where: block lub osobne metody):
   - gdy status OK → decision == "ACCEPT"
   - gdy status FLAGGED → decision == "ACCEPT_WITH_WARNING"
   - gdy status ERROR → decision == "REJECT"

  Co robią testy:
   - każdy test buduje TransactionIngressRequest przez builder
   - wywołuje orchestrator.process(request)
   - sprawdza decision oraz że correlationId i transactionId przechodzą przez cały łańcuch (są takie same w TransactionDecision).
*/


class TransactionAnalysisOrchestratorSpec extends Specification {

    private TransactionAnalyzer analyzer
    private TransactionDecisionPolicy decisionPolicy
    private TransactionAnalysisOrchestrator orchestrator

    def setup() {
        analyzer = new InMemoryTransactionAnalyzer()
        decisionPolicy = new DefaultTransactionDecisionPolicy()
        orchestrator = new TransactionAnalysisOrchestrator(analyzer, decisionPolicy)
    }

    @Unroll
    def "powinien zwrócić decyzje #expectedDecision dla statusu #status"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-001")
                .accountId("ACC-777")
                .correlationId("CORR-123")
                .timestamp(Instant.parse("2026-05-23T10:15:30Z"))
                .payload([analysisStatus: status.name()])
                .build()

        when:
        def decision = orchestrator.process(request)

        then:
        decision != null
        decision.transactionId == request.transactionId
        decision.correlationId == request.correlationId
        decision.decision == expectedDecision
        decision.reason == expectedReason
        decision.decidedAt != null

        where:
        status                   || expectedDecision       | expectedReason
        AnalysisStatus.OK        || "ACCEPT"              | "Status OK - transaction accepted"
        AnalysisStatus.FLAGGED   || "ACCEPT_WITH_WARNING" | "Transaction flagged - manual review required"
        AnalysisStatus.TIMEOUT   || "REJECT"              | "Processing failed - transaction rejected"
        AnalysisStatus.ERROR     || "REJECT"              | "Processing failed - transaction rejected"
    }

    def "powinien zwrocic REJECT gdy payload.analysisStatus jest niepoprawny"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-002")
                .accountId("ACC-888")
                .correlationId("CORR-999")
                .timestamp(Instant.parse("2026-05-23T11:00:00Z"))
                .payload([analysisStatus: "UNKNOWN"])
                .build()

        when:
        def decision = orchestrator.process(request)

        then:
        decision.decision == "REJECT"
        decision.reason == "Processing failed - transaction rejected"
    }

    def "powinien zwrocic ACCEPT gdy payload nie zawiera klucza analysisStatus"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-004")
                .accountId("ACC-111")
                .correlationId("CORR-777")
                .timestamp(Instant.parse("2026-05-23T12:30:00Z"))
                .payload([foo: "bar"])
                .build()

        when:
        def decision = orchestrator.process(request)

        then:
        decision.decision == "ACCEPT"
        decision.reason == "Status OK - transaction accepted"
    }

    def "powinien rzucić wyjątek gdy request jest null"() {
        when:
        orchestrator.process(null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "request cannot be null"
    }

    @Unroll
    def "powinien zwrócić ACCEPT gdy payload jest #caseName (brak analysisStatus)"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-003")
                .accountId("ACC-999")
                .correlationId("CORR-555")
                .timestamp(Instant.parse("2026-05-23T12:00:00Z"))
                .payload(payloadValue)
                .build()

        when:
        def decision = orchestrator.process(request)

        then:
        decision != null
        decision.transactionId == request.transactionId
        decision.correlationId == request.correlationId
        decision.decision == "ACCEPT"
        decision.reason == "Status OK - transaction accepted"
        decision.decidedAt != null

        // - caseName = opis przypadku do raportu, - payloadValue = faktyczna wartość używana w teście.
        where:
        caseName     | payloadValue
        "null"       | null
        "pusty map"  | [:]
    }

    def "powinien obscurity analysisStatus przekazany jako enum i zwrócić ACCEPT_WITH_WARNING"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-005")
                .accountId("ACC-222")
                .correlationId("CORR-888")
                .timestamp(Instant.parse("2026-05-23T13:00:00Z"))
                .payload([analysisStatus: AnalysisStatus.FLAGGED])
                .build()

        when:
        def decision = orchestrator.process(request)

        then:
        decision != null
        decision.transactionId == request.transactionId
        decision.correlationId == request.correlationId
        decision.decision == "ACCEPT_WITH_WARNING"
        decision.reason == "Transaction flagged - manual review required"
        decision.decidedAt != null
    }

}