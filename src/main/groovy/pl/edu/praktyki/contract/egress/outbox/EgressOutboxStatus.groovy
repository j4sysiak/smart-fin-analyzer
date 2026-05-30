package pl.edu.praktyki.contract.egress.outbox

// Ta enum reprezentuje możliwe statusy wiadomości w outboxie egress.
enum EgressOutboxStatus {
    NEW,
    PROCESSING,
    RETRY,
    SENT,
    DEAD
}
