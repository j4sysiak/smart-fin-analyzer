package pl.edu.praktyki.contract

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import pl.edu.praktyki.BaseIntegrationSpec
import spock.lang.Unroll

import java.time.Instant


//test integracyjny całego flow


@TestPropertySource(properties = [
        "banking.rules.amount-threshold=10000"
])
class TransactionAnalysisFlowIntegrationSpec extends BaseIntegrationSpec {

    @Autowired
    TransactionAnalysisOrchestrator orchestrator


    @Unroll
    def "powinien przejsc cala sciezke request -> decision dla kwoty #amount"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId(transactionId)
                .accountId("ACC-INT-001")
                .correlationId(correlationId)
                .timestamp(Instant.parse("2026-05-23T15:00:00Z"))
                .amount(amount)
                .build()

        when:
        def decision = orchestrator.process(request)

        then:
        decision != null
        decision.transactionId == transactionId
        decision.correlationId == correlationId
        decision.decision == expectedDecision
        decision.reason == expectedReason
        decision.decidedAt != null

        where:
        transactionId | correlationId | amount    || expectedDecision       | expectedReason
        "TX-INT-001"  | "CORR-INT-1"  | 9999.99   || "ACCEPT"              | "Status OK - transaction accepted"
        "TX-INT-002"  | "CORR-INT-2"  | 10000.00  || "ACCEPT"              | "Status OK - transaction accepted"
        "TX-INT-003"  | "CORR-INT-3"  | 10000.01  || "ACCEPT_WITH_WARNING" | "Transaction flagged - manual review required"
    }

    def "powinien zwrocic REJECT gdy amount jest null"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-INT-004")
                .accountId("ACC-INT-002")
                .correlationId("CORR-INT-4")
                .timestamp(Instant.parse("2026-05-23T15:30:00Z"))
                .amount(null)
                .build()

        when:
        def decision = orchestrator.process(request)

        then:
        decision != null
        decision.transactionId == request.transactionId
        decision.correlationId == request.correlationId
        decision.decision == "REJECT"
        decision.reason == "Processing failed - transaction rejected"
        decision.decidedAt != null
    }
}