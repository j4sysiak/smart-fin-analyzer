package pl.edu.praktyki.contract.egress.outbox

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.contract.egress.DecisionLogEntity
import pl.edu.praktyki.contract.egress.DecisionLogRepository

// Ta klasa jest odpowiedzialna za dostarczenie payloadu do docelowego systemu
// (np. zapisanie do bazy danych, wysłanie HTTP POST do innego serwisu).
// W tym przykładzie, implementacja jest prosta - zapisuje log decyzji do bazy danych
// do tabeli `decision_log` (reprezentowanej przez `DecisionLogEntity` i `DecisionLogRepository`).
// oraz ze statusem "SENT" lub "FAILED" w tabeli `egress_outbox` (reprezentowanej przez `EgressOutboxEntity` i `EgressOutboxRepository`).
@Service
class EgressDecisionDeliveryService {

    private final DecisionLogRepository decisionLogRepository
    private final MeterRegistry meterRegistry

    EgressDecisionDeliveryService(
            DecisionLogRepository decisionLogRepository,
            MeterRegistry meterRegistry
    ) {
        this.decisionLogRepository = decisionLogRepository
        this.meterRegistry = meterRegistry
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void deliver(DecisionEgressPayload payload) {

/*
Ten fragment (przy if ...) robi prostą idempotencję po correlationId.
Sprawdza, czy payload.correlationId istnieje.
Jeśli tak, pyta repozytorium, czy wpis z takim correlationId już jest w decision_log.
Gdy wpis już istnieje:
 - zwiększa metrykę egress.outbox.delivery.duplicate.skip,
 - dodaje tag decision z wartością decyzji albo UNKNOWN,
 - kończy metodę przez return.
W efekcie dalszy zapis nowego DecisionLogEntity nie następuje.
Praktycznie oznacza to: duplikat wiadomości nie jest przetwarzany drugi raz.
 */
        if (payload.correlationId && decisionLogRepository.existsByCorrelationId(payload.correlationId)) {
            // Duplikat - już istnieje wpis z tym correlationId, więc pomijamy zapis i logujemy metrykę.
            meterRegistry.counter(
                    "egress.outbox.delivery.duplicate.skip",
                    "decision", payload.decision ?: "UNKNOWN"
            ).increment()
            return
        }

        // Zapisujemy log decyzji do bazy danych
        decisionLogRepository.save(new DecisionLogEntity(
                transactionId: payload.transactionId,
                correlationId: payload.correlationId,
                decision: payload.decision,
                reason: payload.reason,
                decidedAt: payload.decidedAt
        ))

        // Zwiększamy metrykę sukcesu dostarczenia wiadomości, z tagiem decyzji
        meterRegistry.counter(
                "egress.outbox.delivery.success.count",
                "decision", payload.decision ?: "UNKNOWN"
        ).increment()
    }
}