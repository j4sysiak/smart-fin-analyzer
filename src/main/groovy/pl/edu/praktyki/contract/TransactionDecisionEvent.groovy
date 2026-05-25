package pl.edu.praktyki.contract

import java.time.Instant

/**
 * Zdarzenie emitowane przez Orchestrator po każdym przetworzeniu każdego requestu.
 *
 * isReplay = false → decyzja obliczona po raz PIERWSZY (nowa)
 * isReplay = true  → decyzja odczytana z idempotency store (powtórzony request)
 *
 * Dzięki tej fladze Egress (Listener) wie czy ma utrwalić wynik w decision_log
 * czy tylko zalogować/zliczyć replay.
 */
class TransactionDecisionEvent {

    // pełna decyzja (transactionId, correlationId, decision, reason, decidedAt)
    final TransactionDecision decision

    // czy to powtórzony request (true) czy pierwsza decyzja (false)
    final boolean replay

    // kiedy event został opublikowany
    final Instant occurredAt

    TransactionDecisionEvent(TransactionDecision decision, boolean replay) {
        this.decision   = decision
        this.replay     = replay
        this.occurredAt = Instant.now()
    }
}