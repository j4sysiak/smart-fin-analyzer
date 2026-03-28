Lab69
-----

Lab69--Asynchroniczne-przetwarzanie-Rozbicie-monolitu-przez-Spring-Events--Architektura-Pub-Sub
-----------------------------------------------------------------------------------------------

Wchodzimy w serce Etapu 4.
To tutaj projekt "Smart-Fin-Analyzer" przestaje być "sznurkiem instrukcji", a staje się ekosystemem.

Wcześniej (w Lab 27) liznęliśmy temat zdarzeń, ale teraz zrobimy to profesjonalnie – w architekturze `Event-Driven`. 
Rozbijemy ścisłe powiązania `Coupling` wewnątrz app:SmartFinFacade.

Problem: 
Obecnie Twoja Fasada jest "dyrygentem", który musi znać wszystkich. 
Wie o walutach, wie o bazie, wie o analityce. 
Jeśli dodasz 10 nowych funkcji (np. wysyłkę SMS, eksport do chmury, integrację z urzędem skarbowym), 
metoda w Fasadzie będzie miała 200 linijek!!!

Rozwiązanie: 
Fasada ma tylko zapisać dane i "rzucić komunikat" w eter: 
"Ej, nowa paczka danych została przetworzona!"  -  kto chce, ten słucha i robi swoje.


Krok 1: Nowoczesne Zdarzenie (TransactionBatchProcessedEvent.groovy)
--------------------------------------------------------------------
Stwórz klasę, która będzie niosła dane o całej przetworzonej partii.
Zdarzenie będzie teraz zawierać gotowy raport i dane, aby słuchacze nie musieli ich liczyć od nowa.
`src/main/groovy/pl/edu/praktyki/event/TransactionBatchProcessedEvent.groovy`:

```groovy
package pl.edu.praktyki.event

import pl.edu.praktyki.domain.Transaction

class TransactionBatchProcessedEvent {
    String userName
    BigDecimal totalBalance
    String generatedReport // Przekazujemy gotowy tekst raportu do procesów w tle
}
```

Krok 2: Odchudzenie Fasady (Publikacja)
---------------------------------------
Zachowujemy Twój return, ale tuż przed nim "rzucamy" event w eter.
`src/main/groovy/pl/edu/praktyki/facade/SmartFinFacade.groovy`:

```groovy
package pl.edu.praktyki.facade

import org.springframework.context.ApplicationEventPublisher // DODAJ IMPORT
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.event.TransactionBatchProcessedEvent // DODAJ IMPORT
import groovy.util.logging.Slf4j

@Service
@Slf4j
class SmartFinFacade {

    @Autowired ApplicationEventPublisher eventPublisher // Publikator zdarzeń Springa
    @Autowired TransactionIngesterService ingester
    @Autowired CurrencyService currencySvc
    @Autowired FinancialAnalyticsService analyticsSvc
    @Autowired ReportGeneratorService reportSvc
    @Autowired pl.edu.praktyki.repository.TransactionRepository repo
    @Autowired TransactionBulkSaver bulkSaver

    String processAndGenerateReport(String userName, List<Transaction> rawTransactions, List<String> rules) {
        log.info(">>> [FASADA] Rozpoczynam kompleksowe przetwarzanie dla użytkownika: {}", userName)

        // 1. Waluty
        rawTransactions.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency ?: "PLN")
            tx.amountPLN = tx.amount * rate
        }

        // 2. Reguły i Import
        List<Transaction> flatListOfTransactions = ingester.ingestAndApplyRules([rawTransactions], rules)

        // 3. Zapis do bazy
        def entities = flatListOfTransactions.collect { tx ->
            new TransactionEntity(
                    originalId: tx.id, date: tx.date, amount: tx.amount, currency: tx.currency,
                    amountPLN: tx.amountPLN, category: tx.category, description: tx.description, tags: tx.tags
            )
        }
        bulkSaver.saveAllInTransaction(entities)

        // 4. Odczyt historii
        def dbRecords = repo.findAll()
        def allHistory = dbRecords.collect { ent ->
            new Transaction(
                    id: ent.originalId, date: ent.date, amount: ent.amount, currency: ent.currency,
                    amountPLN: ent.amountPLN, category: ent.category, description: ent.description, tags: ent.tags
            )
        }

        // 5. Analityka
        def stats =[
                totalBalance: analyticsSvc.calculateTotalBalance(allHistory),
                topCategory: analyticsSvc.getTopSpendingCategory(allHistory),
                spendingMap: analyticsSvc.getSpendingByCategory(allHistory)
        ]

        // 6. Generowanie Raportu
        String finalReport = reportSvc.generateMonthlyReport(userName, stats)

        // ========================================================================
        // NOWOŚĆ: ASYNCHRONICZNE POWIADOMIENIE (Side Effect)
        // Wysyłamy informację o sukcesie, nie czekając na to, co zrobią słuchacze.
        // ========================================================================
        eventPublisher.publishEvent(new TransactionBatchProcessedEvent(
                userName: userName,
                totalBalance: stats.totalBalance,
                generatedReport: finalReport
        ))

        log.info(">>> [FASADA] Przetwarzanie zakończone. Zwracam raport do klienta.")
        return finalReport
    }
}
```

Krok 3: Asynchroniczny Słuchacz (AsyncNotificationService.groovy)
-----------------------------------------------------------------
Ten serwis może teraz np. wysłać ten raport mailem lub zapisać go w zewnętrznym systemie archiwalnym.

Problem: 
Kiedy wywołasz `facade.processAndGenerateReport()`, metoda ta zwróci raport i zakończy się w powiedzmy 100ms. 
Jednak Twój `AsyncNotificationService` dopiero zaczyna pracować w osobnym wątku. 
Jeśli sprawdzisz wynik testu natychmiast, test "nie zauważy" pracy wykonanej w tle.

Rozwiązanie: 
Musimy dodać do serwisu mały "haczyk" (licznik lub flagę), a w teście użyć `Awaitility`, 
aby poczekać, aż asynchroniczna magia się wydarzy.

`src/main/groovy/pl/edu/praktyki/service/AsyncNotificationService.groovy`:

```groovy
package pl.edu.praktyki.service

import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import groovy.util.logging.Slf4j
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import java.util.concurrent.atomic.AtomicInteger // DODAJ TO

@Service
@Slf4j
class AsyncNotificationService {

    // HACZYK: Licznik do celów testowych (AtomicInteger jest bezpieczny dla wielu wątków)
    public final AtomicInteger processedEventsCount = new AtomicInteger(0)

    @Async("bulkTaskExecutor")
    @EventListener
    void handleBatchEvent(TransactionBatchProcessedEvent event) {
        log.info(">>> [ASYNC-EVENT] Rozpoczynam wysyłkę raportu dla: {}", event.userName)

        sleep(1000) // Symulacja pracy

        // Zwiększamy licznik po zakończeniu pracy
        processedEventsCount.incrementAndGet()

        log.info(">>> [ASYNC-EVENT] Praca w tle zakończona. Bilans: {} PLN", event.totalBalance)
    }
}
```


Krok 4: Test Spock (Weryfikacja "Luźnego Powiązania")
-----------------------------------------------------
Jak sprawdzić, czy mechanizm zdarzeń zadziałał?
Teraz napiszemy test, który:
Sprawdzi, czy raport wrócił (synchronizacja).
Poczeka (bez blokowania procesora), aż licznik w serwisie powiadomień wzrośnie (asynchroniczność).
W teście wstrzykniemy SmartFinFacade, wywołamy metodę i sprawdzimy, czy asynchroniczny listener "odbił kartę".

`src/test/groovy/pl/edu/praktyki/event/EventDecouplingSpec.groovy`:

```groovy
package pl.edu.praktyki.event

import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.service.AsyncNotificationService
import org.springframework.beans.factory.annotation.Autowired
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit
import java.time.LocalDate

class EventDecouplingSpec extends BaseIntegrationSpec {

    @Autowired SmartFinFacade facade
    @Autowired AsyncNotificationService notificationService

    def "powinien zwrócić raport synchronicznie i wysłać powiadomienie asynchronicznie"() {
        given: "transakcja testowa"
        def data = [new Transaction(id: "ASYNC-TEST-1", amount: 500, currency: "PLN", category: "Test", date: LocalDate.now())]
        
        // Resetujemy licznik przed testem
        notificationService.processedEventsCount.set(0)

        when: "wywołujemy fasadę"
        String report = facade.processAndGenerateReport("Użytkownik Testowy", data, [])

        then: "1. Raport otrzymujemy natychmiast (synchronicznie)"
        report != null
        report.contains("UŻYTKOWNIK TESTOWY")
        report.contains("500.00 PLN")

        and: "2. W tej milisekundzie powiadomienie jeszcze się nie wysłało (bo śpi 2 sekundy)"
        notificationService.processedEventsCount.get() == 0

        then: "3. Czekamy (max 5s), aż asynchroniczny listener skończy pracę"
        await().atMost(5, TimeUnit.SECONDS).until {
            notificationService.processedEventsCount.get() == 1
        }
        
        and: "Logika w tle została wykonana poprawnie"
        notificationService.processedEventsCount.get() == 1
    }
}
```


Dlaczego to jest "Mid-level Hardcore"?

Decoupling (Rozprzęganie): 
SmartFinFacade nie wie o istnieniu `AsyncNotificationService`. 
Możesz usunąć serwis powiadomień, a Fasada nawet tego nie zauważy. 
To pozwala na pracę wielu zespołów nad jednym kodem bez wchodzenia sobie w paradę.

Spring @Async vs Event Dispatcher: 
Spring najpierw publikuje zdarzenie (synchronizowanie), a adnotacja @Async nad listenerem powoduje, 
że wykonanie jest natychmiast przekazywane do Twojej puli `bulkTaskExecutor`.

Skalowalność: 
Jeśli importowanie danych zajmuje 1s, a wysyłka powiadomień 5s, Twój system nadal procesuje 1 paczkę na sekundę, 
a powiadomienia "doganiają" system w tle.


 
Potwierdzenie nieblokowania: 
Test udowadnia, że użytkownik dostał raport (report != null) zanim serwis powiadomień skończył pracę (count == 0). 
To jest klucz do responsywnych systemów.

Użycie Awaitility: 
Pokazujesz, że nie używasz Thread.sleep(3000), co jest błędem (bo spowalnia testy). 
Używasz inteligentnego oczekiwania, które kończy test w momencie, gdy warunek zostanie spełniony.

Współdzielenie stanu (AtomicInteger): 
Pokazujesz, że rozumiesz problemy wielowątkowości – zwykły int mógłby przekłamać wynik, AtomicInteger jest "thread-safe".

Odpowiedź synchroniczna (UX):
Użytkownik (CLI lub przeglądarka) dostaje raport natychmiast (tak jak chciałeś).

Rozdzielenie odpowiedzialności (Open/Closed):
Jeśli w przyszłości będziesz chciał dodać funkcję: "Wyślij SMS do managera,
jeśli bilans spadnie poniżej zera", nie dopiszesz ani jednej linii do SmartFinFacade.
Po prostu stworzysz nową klasę SmsService z @EventListener, która będzie słuchać tego samego zdarzenia.

Wydajność:
Fasada kończy pracę i "puszcza" wątek HTTP (lub zamyka CLI), a powiadomienia mielą się w tle na Twoim bulkTaskExecutor.

Zadanie dla Ciebie:
-------------------
1. Wdroż `TransactionBatchProcessedEvent`.
2. Zaktualizuj `SmartFinFacade` (dodaj eventPublisher).
3. Stwórz `AsyncNotificationService`.




Jak to przetestować?
--------------------
Uruchom aplikację przez `./gradlew runSmartFinDb -PappArgs="-u Jacek"`

W logach powinieneś zobaczyć:
Logi Fasady (procesowanie).
Wydruk raportu (Twój return).
Pół sekundy później logi [ASYNC-EVENT] z AsyncNotificationService.
 


To jest pierwszy krok do rozbicia monolitu.
W następnym kroku (Lab 70) porozmawiamy o tym, jak te Eventy zamienić na prawdziwe kolejki
(np. symulacja Kafki wewnątrz Springa).


Zadanie dla Ciebie:
-------------------
Dodaj AtomicInteger do AsyncNotificationService.
Uruchom powyższy test.
Zobacz w logach, jak najpierw pojawia się Przetwarzanie zakończone. Zwracam raport do klienta, a dopiero później [ASYNC-EVENT] Rozpoczynam wysyłkę....
To jest kwintesencja architektury Event-Driven! Jeśli to zadziała, jesteśmy gotowi na ostatni krok Etapu 4: Monitorowanie puli wątków (Thread Pool Monitoring). Chcesz zobaczyć, ile zadań aktualnie czeka w Twojej kolejce asynchronicznej? 🚀📈
