package pl.edu.praktyki.contract

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec

import java.time.Instant

/**
 * Test metryk Egress:
 * - nowa decyzja (ACCEPT) → counter inkrementuje się z tagami decision=ACCEPT, replay=false
 * - replay (ACCEPT) → counter inkrementuje się z tagami decision=ACCEPT, replay=true
 * - różne decyzje (ACCEPT, ACCEPT_WITH_WARNING, REJECT) → liczniki oddzielnie
 */
@ActiveProfiles("tc")
class TransactionDecisionEgressMetricsSpec extends BaseIntegrationSpec {

    @Autowired
    TransactionAnalysisOrchestrator orchestrator
    @Autowired
    MeterRegistry meterRegistry


    def setup() {
        // przed każdym testem czyścimy metryki, aby uniknąć wpływu poprzednich testów
        // Jeśli meterRegistry jest współdzielonym beanem Springa, to takie czyszczenie resetuje metryki dla całego kontekstu testowego.
        meterRegistry.meters.toList().each { meter ->
            meterRegistry.remove(meter)
        }
    }

    private static TransactionIngressRequest ingressRequest(
            String transactionId,
            String accountId,
            String correlationId,
            Instant timestamp,
            BigDecimal amount,
            Map payload
    ) {
        TransactionIngressRequest.builder()
                .transactionId(transactionId)
                .accountId(accountId)
                .correlationId(correlationId)
                .timestamp(timestamp)
                .amount(amount)
                .payload(payload)
                .build()
    }

    private def decisionCounter(String decision, String replay) {
        meterRegistry.find("egress.decisions.count")
                .tag("decision", decision)
                .tag("replay", replay)
                .counter()
    }

    // =========================================================================
    // Scenariusz 1: Nowa decyzja ACCEPT → metryka egress.decisions.count++
    //              z tagami: decision=ACCEPT, replay=false
    // =========================================================================
    def "nowa decyzja ACCEPT powinien inkrementować metrykę z tagami"() {
        given:
        def request = ingressRequest(
                "TX-METRICS-001",
                "ACC-METRICS-001",
                "CORR-METRICS-001",
                Instant.parse("2026-05-25T12:00:00Z"),
                100.00G,  // → ACCEPT
                [:]
        )

        when:
        orchestrator.process(request)

        then: "metryka egress.decisions.count ma rekord z decision=ACCEPT, replay=false"
        def counter = decisionCounter("ACCEPT", "false")

        counter != null
        counter.count() >= 1.0d  // co najmniej 1 inkrementacja (może być więcej jeśli inne testy)
    }

    // =========================================================================
    // Scenariusz 2: Replay → metryka inkrementuje się z tagami replay=true
    // =========================================================================
    def "replay powinien inkrementować metrykę z tagami decision=ACCEPT, replay=true"() {
        given:
        def request = ingressRequest(
                "TX-METRICS-002",
                "ACC-METRICS-002",
                "CORR-METRICS-002",
                Instant.parse("2026-05-25T12:01:00Z"),
                100.00G,  // → ACCEPT
                [:]
        )

        when: "pierwszy request — nowa decyzja"
        orchestrator.process(request)

        and: "drugi request — replay"
        def replayRequest = ingressRequest(
                "TX-METRICS-002-B",
                "ACC-METRICS-002",
                "CORR-METRICS-002",
                Instant.parse("2026-05-25T12:02:00Z"),
                100.00G,
                [:]
        )
        orchestrator.process(replayRequest)

        then: "metryka ma rekordy zarówno dla replay=false jak i replay=true"
        def newDecisionCounter = decisionCounter("ACCEPT", "false")
        newDecisionCounter != null
        newDecisionCounter.count() >= 1.0d

        and: "metryka replay=true również istnieje i jest >= 1"
        def replayCounter = decisionCounter("ACCEPT", "true")
        replayCounter != null
        replayCounter.count() >= 1.0d
    }

    // =========================================================================
    // Scenariusz 3: Różne decyzje (ACCEPT, ACCEPT_WITH_WARNING, REJECT)
    //              → osobne liczniki dla każdej kombinacji decision/replay
    // =========================================================================
    def "różne typy decyzji powinny mieć oddzielne liczniki"() {
        given: "request z amount = ACCEPT"
        def acceptRequest = ingressRequest(
                "TX-METRICS-ACCEPT",
                "ACC-METRICS-03",
                "CORR-METRICS-ACCEPT",
                Instant.parse("2026-05-25T12:10:00Z"),
                100.00G,   // → ACCEPT (poniżej progu 10000)
                [:]
        )

        and: "request z amount = ACCEPT_WITH_WARNING (FLAGGED)"
        def flaggedRequest = ingressRequest(
                "TX-METRICS-FLAGGED",
                "ACC-METRICS-04",
                "CORR-METRICS-FLAGGED",
                Instant.parse("2026-05-25T12:11:00Z"),
                50000.00G,  // → FLAGGED (powyżej progu 10000)
                [:]
        )

        when: "przetwarzamy oba requesty"
        orchestrator.process(acceptRequest)
        orchestrator.process(flaggedRequest)

        then: "metryka ACCEPT ma rekord"
        def acceptCounter = decisionCounter("ACCEPT", "false")
        acceptCounter != null
        acceptCounter.count() >= 1.0d

        and: "metryka ACCEPT_WITH_WARNING ma rekord"
        def flaggedCounter = decisionCounter("ACCEPT_WITH_WARNING", "false")
        flaggedCounter != null
        flaggedCounter.count() >= 1.0d
    }
}