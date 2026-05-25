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

    // =========================================================================
    // Scenariusz 1: Nowa decyzja ACCEPT → metryka egress.decisions.count++
    //              z tagami: decision=ACCEPT, replay=false
    // =========================================================================
    def "nowa decyzja ACCEPT powinien inkrementować metrykę z tagami"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-METRICS-001")
                .accountId("ACC-METRICS-001")
                .correlationId("CORR-METRICS-001")
                .timestamp(Instant.parse("2026-05-25T12:00:00Z"))
                .amount(100.00)  // → ACCEPT
                .payload([:])
                .build()

        when:
        orchestrator.process(request)

        then: "metryka egress.decisions.count ma rekord z decision=ACCEPT, replay=false"
        def counter = meterRegistry.find("egress.decisions.count")
                .tag("decision", "ACCEPT")
                .tag("replay", "false")
                .counter()

        counter != null
        counter.count() >= 1.0d  // co najmniej 1 inkrementacja (może być więcej jeśli inne testy)
    }

    // =========================================================================
    // Scenariusz 2: Replay → metryka inkrementuje się z tagami replay=true
    // =========================================================================
    def "replay powinien inkrementować metrykę z tagami decision=ACCEPT, replay=true"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-METRICS-002")
                .accountId("ACC-METRICS-002")
                .correlationId("CORR-METRICS-002")
                .timestamp(Instant.parse("2026-05-25T12:01:00Z"))
                .amount(100.00)  // → ACCEPT
                .payload([:])
                .build()

        when: "pierwszy request — nowa decyzja"
        orchestrator.process(request)

        and: "drugi request — replay"
        def replayRequest = TransactionIngressRequest.builder()
                .transactionId("TX-METRICS-002-B")
                .accountId("ACC-METRICS-002")
                .correlationId("CORR-METRICS-002")
                .timestamp(Instant.parse("2026-05-25T12:02:00Z"))
                .amount(100.00)
                .payload([:])
                .build()
        orchestrator.process(replayRequest)

        then: "metryka ma rekordy zarówno dla replay=false jak i replay=true"
        def newDecisionCounter = meterRegistry.find("egress.decisions.count")
                .tag("decision", "ACCEPT")
                .tag("replay", "false")
                .counter()
        newDecisionCounter != null
        newDecisionCounter.count() >= 1.0d

        and: "metryka replay=true również istnieje i jest >= 1"
        def replayCounter = meterRegistry.find("egress.decisions.count")
                .tag("decision", "ACCEPT")
                .tag("replay", "true")
                .counter()
        replayCounter != null
        replayCounter.count() >= 1.0d
    }

    // =========================================================================
    // Scenariusz 3: Różne decyzje (ACCEPT, ACCEPT_WITH_WARNING, REJECT)
    //              → osobne liczniki dla każdej kombinacji decision/replay
    // =========================================================================
    def "różne typy decyzji powinny mieć oddzielne liczniki"() {
        given: "request z amount = ACCEPT"
        def acceptRequest = TransactionIngressRequest.builder()
                .transactionId("TX-METRICS-ACCEPT")
                .accountId("ACC-METRICS-03")
                .correlationId("CORR-METRICS-ACCEPT")
                .timestamp(Instant.parse("2026-05-25T12:10:00Z"))
                .amount(100.00)   // → ACCEPT (poniżej progu 10000)
                .payload([:])
                .build()

        and: "request z amount = ACCEPT_WITH_WARNING (FLAGGED)"
        def flaggedRequest = TransactionIngressRequest.builder()
                .transactionId("TX-METRICS-FLAGGED")
                .accountId("ACC-METRICS-04")
                .correlationId("CORR-METRICS-FLAGGED")
                .timestamp(Instant.parse("2026-05-25T12:11:00Z"))
                .amount(50000.00)  // → FLAGGED (powyżej progu 10000)
                .payload([:])
                .build()

        when: "przetwarzamy oba requesty"
        orchestrator.process(acceptRequest)
        orchestrator.process(flaggedRequest)

        then: "metryka ACCEPT ma rekord"
        def acceptCounter = meterRegistry.find("egress.decisions.count")
                .tag("decision", "ACCEPT")
                .counter()
        acceptCounter != null
        acceptCounter.count() >= 1.0d

        and: "metryka ACCEPT_WITH_WARNING ma rekord"
        def flaggedCounter = meterRegistry.find("egress.decisions.count")
                .tag("decision", "ACCEPT_WITH_WARNING")
                .counter()
        flaggedCounter != null
        flaggedCounter.count() >= 1.0d
    }
}