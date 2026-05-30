package pl.edu.praktyki.contract.egress.outbox

import java.time.Instant

// Ta klasa reprezentuje strukturę danych, która jest używana jako payload do wysyłania informacji
// o decyzji dotyczącej transakcji do systemu zewnętrznego.
// Payload, który jest serializowany do JSON i zapisywany w tabeli egress_outbox,
// a następnie wysyłany do systemu zewnętrznego (np. przez REST API lub Kafka).
class DecisionEgressPayload {
    String transactionId
    String correlationId
    String decision
    String reason
    Instant decidedAt
    boolean replay
    Instant occurredAt
}