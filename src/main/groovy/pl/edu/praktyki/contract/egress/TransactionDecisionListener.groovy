package pl.edu.praktyki.contract.egress

import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import pl.edu.praktyki.contract.TransactionDecisionEvent

/**
 * Warstwa EGRESS — nasłuchuje na TransactionDecisionEvent i:
 * 1. Loguje wynik (zawsze, niezależnie od replay)
 * 2. Inkrementuje metrykę Micrometer z tagami (zawsze)
 * 3. Persists DecisionLogEntity — TYLKO dla nowych decyzji (isReplay = false)
 *
 * Dlaczego nie zapisujemy replay?
 * Idempotency store (idempotency_keys) już gwarantuje, że decyzja jest
 * utrwalona. decision_log to audit trail NOWYCH zdarzeń, nie retried requestów.
 */
@Component
@Slf4j
class TransactionDecisionListener {

/*
Co tu się dzieje?
  - Nasłuchujemy na TransactionDecisionEvent, który zawiera pełną decyzję i flagę isReplay.
  - Zawsze logujemy decyzję (ważne dla diagnostyki, zarówno nowych jak i powtórzonych requestów).
  - Zawsze inkrementujemy metrykę "egress.decisions.count" z tagami "decision" i "replay" (umożliwia analizę w Actuator/Prometheus).
  - TYLKO dla nowych decyzji (isReplay = false) tworzymy i zapisujemy DecisionLogEntity do bazy.
    Replay-e są pomijane w zapisie, ale nadal logowane i zliczane w metrykach.

Dlaczego tak?
- Idempotency store (idempotency_keys) już zapewnia trwałość decyzji dla powtórzonych requestów,
  więc nie ma potrzeby duplikować tych danych w decision_log.
- decision_log ma być append-only auditem nowych decyzji, a nie powtórzonych requestów,
  które nie wnoszą nowych informacji, ale mogą być istotne diagnostycznie (stąd logowanie i metryki dla replay).

 */

    private final DecisionLogRepository decisionLogRepository
    private final MeterRegistry meterRegistry

    TransactionDecisionListener(
            DecisionLogRepository decisionLogRepository,
            MeterRegistry meterRegistry
    ) {
        this.decisionLogRepository = decisionLogRepository
        this.meterRegistry         = meterRegistry
    }

    @EventListener
    void onDecision(TransactionDecisionEvent event) {
        def d = event.decision

        // 1. Zawsze loguj (nowa decyzja lub replay — oba są istotne diagnostycznie)
        log.info(
                "EGRESS | decision={} | correlationId={} | transactionId={} | replay={} | reason={}",
                d.decision, d.correlationId, d.transactionId, event.replay, d.reason
        )



/*
Micrometer zapisuje tu metrykę licznikową (counter), czyli prosty licznik zdarzeń.

Co to są metryki:
Metryki to numeryczne dane o działaniu aplikacji, np.:
 - liczba decyzji,
 - liczba błędów,
 - czas odpowiedzi,
 - liczba requestów.

Służą do obserwowalności aplikacji.
Co robi ten fragment
Tutaj zwiększany jest licznik egress.decisions.count za każdym razem, gdy pojawi się decyzja.
Licznik ma tagi:
 - decision - np. APPROVED, REJECTED,
 - replay - czy zdarzenie było replayem.

To pozwala potem policzyć np.:
 - ile było wszystkich decyzji,
 - ile było APPROVED vs REJECTED,
 - ile zdarzeń było replayami,
 - czy replaye nagle nie zaczęły rosnąć.

Po co inkrementujemy
 - Bo chcemy zliczyć wystąpienia zdarzenia.
 - Bez inkrementacji nie byłoby danych liczbowych do monitoringu.
 */

        // 2. Zawsze inkrementuj metrykę — tagi pozwalają filtrować w Actuator/Prometheus
        meterRegistry.counter(
                "egress.decisions.count",
                "decision", d.decision ?: "UNKNOWN",
                "replay",   String.valueOf(event.replay)
        ).increment()

        // 3. Utrwal TYLKO nowe decyzje (replay nie duplikuje wpisu w decision_log)
        if (!event.replay) {
            def entity = new DecisionLogEntity(
                    transactionId: d.transactionId,
                    correlationId: d.correlationId,
                    decision:      d.decision,
                    reason:        d.reason,
                    decidedAt:     d.decidedAt
            )
            decisionLogRepository.save(entity)
            log.debug("EGRESS | decision_log saved | correlationId={}", d.correlationId)
        } else {
            log.debug("EGRESS | replay detected, decision_log skipped | correlationId={}", d.correlationId)
        }
    }
}