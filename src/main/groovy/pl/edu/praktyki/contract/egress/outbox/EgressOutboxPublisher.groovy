package pl.edu.praktyki.contract.egress.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.contract.TransactionDecisionEvent

import java.time.Instant

// Ta klasa jest odpowiedzialna za przyjmowanie eventów biznesowych
// (w tym przypadku `TransactionDecisionEvent`), mapowanie ich na format docelowy (np. `DecisionEgressPayload`)
// i zapisywanie do tabeli outboxa (`egress_outbox`), skąd później będą odbierane przez `EgressOutboxDispatcher` i przetwarzane przez `EgressOutboxProcessor`.
@Component
@Slf4j
class EgressOutboxPublisher {

    private final EgressOutboxRepository outboxRepository
    private final ObjectMapper objectMapper

    EgressOutboxPublisher(EgressOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository
        this.objectMapper = objectMapper
    }

    @Transactional
    void enqueue(TransactionDecisionEvent event) {
        def d = event.decision

        def payload = new DecisionEgressPayload(
                transactionId: d.transactionId,
                correlationId: d.correlationId,
                decision: d.decision,
                reason: d.reason,
                decidedAt: d.decidedAt,
                replay: event.replay,
                occurredAt: event.occurredAt
        )

        def entity = new EgressOutboxEntity(
                eventId: UUID.randomUUID().toString(),
                eventType: "TransactionDecisionEvent",
                transactionId: d.transactionId,
                correlationId: d.correlationId,
                payloadJson: objectMapper.writeValueAsString(payload),
                status: EgressOutboxStatus.NEW,
                nextAttemptAt: Instant.now()
        )

        try {
            outboxRepository.save(entity)
            log.debug("EGRESS-OUTBOX | enqueued | correlationId={}", d.correlationId)
        } catch (DataIntegrityViolationException ex) {
            // np. duplikat przy ponownym enqueue tego samego eventu biznesowego
            log.warn("EGRESS-OUTBOX | duplicate enqueue skipped | correlationId={}", d.correlationId)
        }
    }
}