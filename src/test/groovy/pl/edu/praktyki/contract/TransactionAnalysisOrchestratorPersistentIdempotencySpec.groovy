package pl.edu.praktyki.contract

import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyRepository

import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TransactionAnalysisOrchestratorPersistentIdempotencySpec extends BaseIntegrationSpec {

    @Autowired
    TransactionAnalysisOrchestrator orchestrator

    @Autowired
    IdempotencyKeyRepository idempotencyKeyRepository

    def setup() {
        // Dodatkowa izolacja dla tego testu
        idempotencyKeyRepository.deleteAll()
    }

    def "powinien zwrócić ten sam wynik dla tego samego correlationId nawet gdy drugi request ma inne dane"() {
        given:
        def firstRequest = TransactionIngressRequest.builder()
                .transactionId("TX-PERSIST-001")
                .accountId("ACC-PERSIST-001")
                .correlationId("CORR-PERSIST-001")
                .timestamp(Instant.parse("2026-05-23T20:00:00Z"))
                .amount(9999.99) // -> ACCEPT
                .payload([:])
                .build()

        and:
        def secondRequest = TransactionIngressRequest.builder()
                .transactionId("TX-PERSIST-001")
                .accountId("ACC-PERSIST-001")
                .correlationId("CORR-PERSIST-001") // ten sam key
                .timestamp(Instant.parse("2026-05-23T20:01:00Z"))
                .amount(50000.00) // normalnie byłby FLAGGED, ale nie powinien wejść do analizy
                .payload([:])
                .build()

        when:
        def firstDecision = orchestrator.process(firstRequest)
        def secondDecision = orchestrator.process(secondRequest)

        then:
        firstDecision.decision == "ACCEPT"
        secondDecision.decision == "ACCEPT"

        and: "idempotent zwraca ten sam utrwalony wynik"
        secondDecision.transactionId == firstDecision.transactionId
        secondDecision.correlationId == firstDecision.correlationId
        secondDecision.reason == firstDecision.reason
        Math.abs(secondDecision.decidedAt.toEpochMilli() - firstDecision.decidedAt.toEpochMilli()) <= 1L

        and: "w DB tylko jeden rekord idempotency key"
        idempotencyKeyRepository.count() == 1
    }

    def "powinien zachować 1 rekord idempotency przy 2 !!równoległych!! (to ważne) requestach z tym samym correlationId"() {
        given:
        String correlationId = "CORR-RACE-001"

        def requestA = TransactionIngressRequest.builder()
                .transactionId("TX-RACE-001")
                .accountId("ACC-RACE-001")
                .correlationId(correlationId)
                .timestamp(Instant.parse("2026-05-24T10:00:00Z"))
                .amount(9999.99) // potencjalnie ACCEPT
                .payload([source: "A"])
                .build()

        def requestB = TransactionIngressRequest.builder()
                .transactionId("TX-RACE-002")
                .accountId("ACC-RACE-001")
                .correlationId(correlationId)
                .timestamp(Instant.parse("2026-05-24T10:00:01Z"))
                .amount(50000.00) // potencjalnie FLAGGED
                .payload([source: "B"])
                .build()

        def pool = Executors.newFixedThreadPool(2)
        def ready = new CountDownLatch(2)
        def start = new CountDownLatch(1)

        when:
        /*
          ten fragment:
          1.
          - Uruchamia zadanie asynchroniczne w puli `pool`:  def pool = Executors.newFixedThreadPool(2)
          - Tworzy futureA, czyli uchwyt do wyniku przyszłego wywołania
          2.
          - W środku zadania:
              - ready.countDown() --> sygnalizuje, że wątek jest gotowy
              - start.await(5, TimeUnit.SECONDS) -->czeka na wspólny sygnał startu maksymalnie 5 sekund
              - po zwolnieniu blokady wykonuje orchestrator.process(requestA)
          3.
          - Rzutowanie do Callable<TransactionDecision> mówi, że:
              - to zadanie ma zwrócić wartość
              - zwracanym typem ma być TransactionDecision.
          4.
          Cel testowy:
              - oba requesty mają wystartować możliwie jednocześnie
              - żeby zasymulować wyścig współbieżny dla tego samego correlationId
              - a potem sprawdzić, czy idempotencja działa poprawnie.
         */
        def futureA = pool.submit({
            ready.countDown()
            start.await(5, TimeUnit.SECONDS)
            orchestrator.process(requestA)
        } as Callable<TransactionDecision>)

        def futureB = pool.submit({
            ready.countDown()
            start.await(5, TimeUnit.SECONDS)
            orchestrator.process(requestB)
        } as Callable<TransactionDecision>)

        assert ready.await(5, TimeUnit.SECONDS)
        start.countDown()

        TransactionDecision decisionA = futureA.get(10, TimeUnit.SECONDS)
        TransactionDecision decisionB = futureB.get(10, TimeUnit.SECONDS)

        def persisted = idempotencyKeyRepository.findByCorrelationId(correlationId).orElse(null)

        then:
        persisted != null

        and: "niezależnie od wyścigu oba requesty dostają ten sam utrwalony wynik"
        decisionA.correlationId == correlationId
        decisionB.correlationId == correlationId
        decisionA.decision == decisionB.decision
        decisionA.reason == decisionB.reason
        Math.abs(decisionA.decidedAt.toEpochMilli() - decisionB.decidedAt.toEpochMilli()) <= 1L

        and: "w bazie tylko jeden rekord idempotency"
        idempotencyKeyRepository.count() == 1

        and: "zwracany wynik odpowiada rekordowi utrwalonemu w DB"
        decisionA.transactionId == persisted.transactionId
        decisionA.decision == persisted.decision
        decisionA.reason == persisted.reason
        Math.abs(decisionA.decidedAt.toEpochMilli() - persisted.decidedAt.toEpochMilli()) <= 1L

        cleanup:
        pool.shutdownNow()
    }


    /*
    given
    Tworzymy:
      - demoniczny correlationId = "CORR-STRESS-015"
      - pulę 15 wątków
      - dwa zatrzaski (ready, start)
      - listę błędów thread-safe (CopyOnWriteArrayList)

    when
    Każdy z 15 wątków woła:
      - ready.countDown() (sygnalizuje gotowość)
      - a potem blokuje się na start.await().
    Gdy wszystkie 15 są gotowe, główny wątek odpala start.countDown() – pistolet startowy – i wszyscy ruszają jednocześnie z tym samym correlationId

    then
    Sprawdzamy:
      - brak wyjątków
      - wszystkie 15 decisions != null
      - identyczny decision/reason/decidedAt we wszystkich odpowiedziach, a w bazie dokładnie 1 rekord idempotency

    */



    def "stress: powinien zachować 1 rekord idempotency przy 15 równoległych requestach z tym samym correlationId"() {
        given: "wspólny correlationId dla wszystkich wątków"
        String correlationId = "CORR-STRESS-015"
        int threadCount = 15

        and: "pula wątków gotowa na jednoczesny strzał"
        def pool = Executors.newFixedThreadPool(threadCount)
 /*
        W tym teście są dwa "zatrzaski":
        1.
        ready = new CountDownLatch(threadCount)
          - liczy, ile wątków jest już gotowych,
          - każdy wątek robi ready.countDown()
          - główny wątek czeka na ready.await() - efekt: test wie, że wszystkie wątki ustawiły się do startu.
        2.
        start = new CountDownLatch(1)
            - działa jak wspólny sygnał startu,
            - wszystkie wątki czekają na start.await(),
            - gdy główny wątek zrobi start.countDown(), wszystkie ruszają prawie jednocześnie.

        3.
        Po co to tutaj:
        Celem jest zasymulowanie wyścigu współbieżnego:
          - wiele requestów ma ten sam correlationId
          - wszystkie startują niemal naraz
          - dzięki temu można sprawdzić, czy idempotencja działa poprawnie pod obciążeniem

        4.
        Intuicja, to działa jak:
        ready = wszyscy zawodnicy meldują gotowość
        start = strzał startera
*/
        def ready  = new CountDownLatch(threadCount)  // zatrzask do synchronizacji startu wątków – każdy wątek robi ready.countDown() gdy jest gotowy, a główny czeka na ready.await() aż wszyscy będą gotowi
        def start  = new CountDownLatch(1)      // zatrzask do wspólnego startu – wszystkie wątki czekają na start.await(), a główny robi start.countDown() żeby ruszyły niemal jednocześnie
        def errors = new CopyOnWriteArrayList<Throwable>()  // lista do zbierania wyjątków z wątków – CopyOnWriteArrayList jest thread-safe, więc można bezpiecznie dodawać wyjątki z różnych wątków

        when: "wszystkie wątki ustawiają się na linii startu i ruszają jednocześnie"
        def futures = (1..threadCount).collect { idx ->
            pool.submit({
                ready.countDown()
                start.await(10, TimeUnit.SECONDS)

                def req = TransactionIngressRequest.builder()
                        .transactionId("TX-STRESS-${String.format('%03d', idx)}")
                        .accountId("ACC-STRESS-001")
                        .correlationId(correlationId)
                        .timestamp(Instant.parse("2026-05-24T12:00:00Z"))
                        .amount(9999.99)           // → ACCEPT
                        .payload([thread: idx])
                        .build()

                orchestrator.process(req)
            } as Callable<TransactionDecision>)
        }

        assert ready.await(10, TimeUnit.SECONDS) : "wątki nie zebrały się na czas"
        start.countDown()    // pistolet startowy – ruszają wszyscy równocześnie

        List<TransactionDecision> decisions = futures.collect { f ->
            try {
                f.get(20, TimeUnit.SECONDS)
            } catch (Throwable t) {
                errors.add(t)
                null
            }
        }

        then: "żaden wątek nie zgłosił wyjątku"
        errors.isEmpty()

        and: "wszystkie ${threadCount} requestów dostało odpowiedź"
        decisions.every { it != null }

        and: "każdy request dostał ten sam correlationId i tę samą decyzję"
        decisions.every { it.correlationId == correlationId }
        decisions.collect { it.decision }.unique().size() == 1
        decisions.collect { it.reason   }.unique().size() == 1

        and: "wszystkie decidedAt są identyczne (pochodzi z jednego utrwalonego rekordu)"
        long firstEpoch = decisions[0].decidedAt.toEpochMilli()
        decisions.every { Math.abs(it.decidedAt.toEpochMilli() - firstEpoch) <= 1L }

        and: "w bazie danych dokładnie jeden rekord idempotency key"
        idempotencyKeyRepository.count() == 1

        and: "rekord w DB pasuje do zwróconej decyzji"
        def persisted = idempotencyKeyRepository.findByCorrelationId(correlationId).orElse(null)
        persisted != null
        decisions[0].transactionId == persisted.transactionId
        decisions[0].decision      == persisted.decision
        decisions[0].reason        == persisted.reason
        Math.abs(decisions[0].decidedAt.toEpochMilli() - persisted.decidedAt.toEpochMilli()) <= 1L

        cleanup:
        pool.shutdownNow()
    }

/*
    Kluczowe cechy tego testu:
    2 fale (Wave 1 + Wave 2) – każda fala uruchamia 15 requestów równolegle
    Ten sam correlationId – dla obu fal → system musi zwrócić identyczną decyzję
    Różne dane biznesowe – fala 2 wysyła amount: 50000.00 (normalnie FLAGGED), ale idempotency zwraca ACCEPT z fali 1
    7 faz asercji – od braku błędów do pełnej spójności DB
            Wymusza wyścig współbieżny – oba zatrzaski (ready, start) gwarantują równoległy start
    Cel testu:
     - Sprawdzić, czy idempotencja działa poprawnie pod obciążeniem
     - Czy system zwraca ten sam wynik dla wielu równoległych requestów z tym samym correlationId, nawet jeśli dane w requestach są różne
     - Czy w bazie jest tylko 1 rekord idempotency i czy jego dane pasują do zwróconych decyzji tabela w bazie to: `idempotency_keys`
 */
    def "stress: powinien zwrócić identyczny wynik dla 2 fal retried requestów z tym samym correlationId"() {
        given: "wspólny correlationId i waveSize = 15, razem 30 requestów"
        String correlationId = "CORR-RETRY-STORM-030"
        int waveSize = 15

        and: "pula wątków i zatrzaski do synchronizacji"
        def pool = Executors.newFixedThreadPool(waveSize)
        def errors = new CopyOnWriteArrayList<Throwable>()


/*
Ten fragment testu przygotowuje start pierwszej fali równoległych requestów.
- when: ...
       Sekcja Spocka opisująca akcję wykonywaną w teście. Tekst po when: jest tylko etykietą opisową.

- def ready1 = new CountDownLatch(waveSize)
   - Tworzy licznik ustawiony na waveSize (tu: 15)
   - Każdy wątek po uruchomieniu wywołuje ready1.countDown(), żeby zgłosić: „jestem gotowy”
   - Kod główny może potem czekać na ready1.await(), aż wszystkie wątki się przygotują

- def start1 = new CountDownLatch(1)
   - Tworzy wspólny „sygnał startu”
   - Wszystkie wątki czekają na start1.await()
   - Gdy test wykona start1.countDown(), wszystkie ruszają prawie jednocześnie

  Cel:
    - zsynchronizować 15 równoległych requestów
    - uruchomić je możliwie w tym samym momencie
    - zasymulować wyścig współbieżny dla tego samego correlationId
 */


        when: "fala 1: startuje 15 requestów równolegle z tym samym correlationId, ale różnymi TX-ID i amount ACCEPT"
        def ready1 = new CountDownLatch(waveSize) // zatrzask do synchronizacji startu pierwszej fali – każdy wątek robi ready1.countDown() gdy jest gotowy, a główny czeka na ready1.await() aż wszyscy będą gotowi
        def start1 = new CountDownLatch(1)  // zatrzask do wspólnego startu pierwszej fali – wszystkie wątki czekają na start1.await(), a główny robi start1.countDown() żeby ruszyły niemal jednocześnie

        def futuresWave1 = (1..waveSize).collect { idx ->
            pool.submit({
                ready1.countDown()  // sygnalizuje gotowość tego wątku
                start1.await(10, TimeUnit.SECONDS)  // czeka na sygnał startu, max 10 sekund

                def req = TransactionIngressRequest.builder()
                        .transactionId("TX-W1-${String.format('%03d', idx)}")
                        .accountId("ACC-RETRY-STORM")
                        .correlationId(correlationId)
                        .timestamp(Instant.parse("2026-05-25T10:00:00Z"))
                        .amount(9999.99)  // → ACCEPT
                        .payload([wave: 1, thread: idx])
                        .build()

                orchestrator.process(req)
            } as Callable<TransactionDecision>)  // rzutujemy na Callable<TransactionDecision>, bo chcemy, żeby każde zadanie zwracało TransactionDecision, a nie było Runnable (które nic nie zwraca)
        }

/*
To synchronizuje start pierwszej fali wątków.
ready1.await(10, TimeUnit.SECONDS) czeka maksymalnie 10 sekund, aż wszystkie wątki z fali 1 wykonają wcześniej ready1.countDown().
assert ... : "wave 1: wątki nie zebrały się na czas" przerywa test z podanym komunikatem, jeśli nie wszystkie wątki zdążą zgłosić gotowość w czasie.
start1.countDown() zwalnia drugi zatrzask i daje wspólny sygnał startu wszystkim oczekującym wątkom.
Efekt:
 - najpierw test upewnia się, że wszystkie wątki są gotowe,
 - potem uruchamia je prawie jednocześnie,
 - dzięki temu da się zasymulować wyścig współbieżny.
 */

        assert ready1.await(10, TimeUnit.SECONDS) : "wave 1: wątki nie zebrały się na czas"
        start1.countDown()

/*
        Po tej fazie mamy 15 requestów z fali 1, które powinny dać ten sam wynik (np. ACCEPT) i utrwalić go w DB.
         - Każdy request ma inny TX-ID, ale ten sam correlationId.
         - Wszystkie powinny zwrócić tę samą decyzję i reason, a w bazie powinien być tylko 1 rekord idempotency.
         - To sprawdza, czy idempotencja działa poprawnie przy wielu równoległych requestach z tym samym correlationId.
         - Po tej fazie możemy też sprawdzić, czy w bazie jest dokładnie 1 rekord idempotency i czy jego dane pasują do zwróconych decyzji.

Ten fragment zbiera wyniki z futuresWave1 do listy decisionsWave1.
futuresWave1.collect { ... } przechodzi po każdym Future
f.get(20, TimeUnit.SECONDS) czeka maksymalnie 20 sekund na wynik danego zadania
jeśli zadanie zakończy się poprawnie, do listy trafia TransactionDecision
jeśli poleci wyjątek, trafia on do errors, a w miejsce wyniku wpisywane jest null
 */

        List<TransactionDecision> decisionsWave1 = futuresWave1.collect { f ->
            try {
                f.get(20, TimeUnit.SECONDS)
            } catch (Throwable t) {
                errors.add(t)
                null
            }
        }

        // fala 2: retry z tym samym correlationId, ale innymi danymi (różne TX-ID, inny amount)

        and: "fala 2: retry z tym samym correlationId, ale inne dane (różne TX-ID, inny amount)"
        def ready2 = new CountDownLatch(waveSize)
        def start2 = new CountDownLatch(1)

        def futuresWave2 = (1..waveSize).collect { idx ->
            pool.submit({
                ready2.countDown()
                start2.await(10, TimeUnit.SECONDS)

                def req = TransactionIngressRequest.builder()
                        .transactionId("TX-W2-${String.format('%03d', idx)}")  // inne TX-ID !!!
                        .accountId("ACC-RETRY-STORM")
                        .correlationId(correlationId)  // ten sam correlationId
                        .timestamp(Instant.parse("2026-05-25T10:00:05Z"))
                        .amount(50000.00)  // inny amount (normalnie FLAGGED, ale idempotency powinien zwrócić ACCEPT z fali 1)
                        .payload([wave: 2, thread: idx])
                        .build()

                orchestrator.process(req)
            } as Callable<TransactionDecision>)
        }

        assert ready2.await(10, TimeUnit.SECONDS) : "wave 2: wątki nie zebrały się na czas"
        start2.countDown()

        List<TransactionDecision> decisionsWave2 = futuresWave2.collect { f ->
            try {
                f.get(20, TimeUnit.SECONDS)
            } catch (Throwable t) {
                errors.add(t)
                null
            }
        }

        List<TransactionDecision> allDecisions = decisionsWave1 + decisionsWave2
        def persisted = idempotencyKeyRepository.findByCorrelationId(correlationId).orElse(null)

        then: "faza 1: brak wyjątków"
        errors.isEmpty()

        and: "faza 2: komplet 30 odpowiedzi (15 z fali 1 + 15 z fali 2)"
        allDecisions.size() == 30
        allDecisions.every { it != null }

        and: "faza 3: spójność idempotency – wszystkie requesty (niezależnie z której fali) dostały TĘ SAMĄ decyzję"
        allDecisions.every { it.correlationId == correlationId }
        assert allDecisions.collect { it.decision }.unique().size() == 1 : "decision powinny być identyczne"
        assert allDecisions.collect { it.reason }.unique().size() == 1 : "reason powinny być identyczne"

        and: "faza 4: spójność czasu – wszystkie decidedAt pochodzą z jednego rekordu (różnice <= 1ms)"
        long firstEpoch = allDecisions[0].decidedAt.toEpochMilli()
        allDecisions.every { Math.abs(it.decidedAt.toEpochMilli() - firstEpoch) <= 1L }

        and: "faza 5: spójność z bazą danych – dokładnie 1 rekord idempotency"
        idempotencyKeyRepository.count() == 1
        persisted != null

        and: "faza 6: zwrócone transactionId powinno być z PIERWSZEJ fali (z pierwszego requestu), wszystkie requesty dostały tę samą wartość"
        allDecisions.every { it.transactionId == persisted.transactionId }
        assert allDecisions.every { it.transactionId == decisionsWave1[0].transactionId } : "wszystkie requesty zwracają TX-ID z fali 1"

        and: "faza 7: finalna weryfikacja – decyzja, reason i timestamp są dokładnie identyczne w całym zestawie"
        allDecisions.every { it.decision == persisted.decision }
        allDecisions.every { it.reason == persisted.reason }
        Math.abs(allDecisions[0].decidedAt.toEpochMilli() - persisted.decidedAt.toEpochMilli()) <= 1L

        cleanup:
        pool.shutdownNow()
    }
}