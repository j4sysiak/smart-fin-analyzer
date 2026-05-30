package pl.edu.praktyki.contract.egress.outbox

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.contract.TransactionAnalysisOrchestrator
import pl.edu.praktyki.contract.TransactionIngressRequest
import pl.edu.praktyki.contract.egress.DecisionLogRepository
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyRepository

import java.time.Instant

@ActiveProfiles("tc")
@TestPropertySource(properties = [
        "app.egress.outbox.enabled=false"
])

// ten test sprawdza, czy po przetworzeniu żądania z replay=false, zostaje utworzony rekord w tabeli outboxa egress z status NEW,
// a przy ponownym przetworzeniu tego samego żądania z replay=true, nie jest tworzony dodatkowy rekord.
// Co pokrywa ten test:
//  - replay=false -> tworzy 1 rekord w egress_outbox (NEW)
//  - replay=true -> nie tworzy dodatkowego rekordu

class EgressOutboxEnqueueSpec extends BaseIntegrationSpec {

    @Autowired TransactionAnalysisOrchestrator orchestrator
    @Autowired EgressOutboxRepository outboxRepository
    @Autowired DecisionLogRepository decisionLogRepository
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository

    def setup() {
        outboxRepository.deleteAll()
        decisionLogRepository.deleteAll()
        idempotencyKeyRepository.deleteAll()
    }

    // ten test sprawdza, czy po przetworzeniu żądania z replay=false, zostaje utworzony rekord w tabeli outboxa egress z status NEW,
    // a przy ponownym przetworzeniu tego samego żądania z replay=true, nie jest tworzony dodatkowy rekord.
    // enqueuowac (kolejkować) -> tworzyć rekord w tabeli outboxa z status NEW
    def "replay=false powinien enqueuowac (kolejkować) 1 rekord do outbox"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-OUTBOX-ENQ-001")
                .accountId("ACC-OUTBOX-ENQ-001")
                .correlationId("CORR-OUTBOX-ENQ-001")
                .timestamp(Instant.parse("2026-05-25T12:00:00Z"))
                .amount(100.00)
                .payload([:])
                .build()

        when:
        def decision = orchestrator.process(request)

        then:
        decision != null
        decision.decision == "ACCEPT"

        and: "w outbox jest dokładnie jeden rekord NEW"
        outboxRepository.count() == 1
        def row = outboxRepository.findAll().first()
        row.correlationId == "CORR-OUTBOX-ENQ-001"
        row.transactionId == "TX-OUTBOX-ENQ-001"
        row.status == EgressOutboxStatus.NEW
        row.attemptCount == 0
        row.payloadJson?.contains('"correlationId":"CORR-OUTBOX-ENQ-001"')

        and: "decision_log jest puste, bo dostarczanie robi dopiero processor"
        decisionLogRepository.countByCorrelationId("CORR-OUTBOX-ENQ-001") == 0
    }

    // ten test sprawdza, czy po przetworzeniu żądania z replay=false,
    // zostaje utworzony rekord w tabeli outboxa egress z status NEW,
    def "replay=true nie powinien dodawac kolejnego rekordu do outbox"() {
        given:
        def first = TransactionIngressRequest.builder()
                .transactionId("TX-OUTBOX-ENQ-002")
                .accountId("ACC-OUTBOX-ENQ-002")
                .correlationId("CORR-OUTBOX-ENQ-002")
                .timestamp(Instant.parse("2026-05-25T12:01:00Z"))
                .amount(100.00)
                .payload([:])
                .build()

        def replay = TransactionIngressRequest.builder()
                .transactionId("TX-OUTBOX-ENQ-002-REPLAY")
                .accountId("ACC-OUTBOX-ENQ-002")
                .correlationId("CORR-OUTBOX-ENQ-002")
                .timestamp(Instant.parse("2026-05-25T12:02:00Z"))
                .amount(99999.00)
                .payload([replay: true])
                .build()

        when:
        orchestrator.process(first)
        orchestrator.process(replay)

        then:
        outboxRepository.count() == 1
        outboxRepository.findAll().first().transactionId == "TX-OUTBOX-ENQ-002"
    }
}

