Lab85
-----

Lab85--Zarządzanie-Pulą-Wątków--Observability-Pattern--Kto-ostatnio-co-robił-w-tle
==================================================================================


Tak mi poradzil ChatGpt, jeżeli chodzi o logowanie wątków:
w Klasach, gdzie używam metod z adnotacj @Async wstrzyknij obiekt klasy ThreadTracker:  @Autowired ThreadTracker threadTracker.
to jest cialo tej klasy:

```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Prosty bean do śledzenia informacji o wątkach uruchamiających asynchroniczne zadania.
 * Używany zamiast duplikowania ConcurrentHashMap w wielu klasach.
 */
@Component
class ThreadTracker {
    // przechowujemy obiekty (mapy) z dodatkowymi metadanymi: thread, timestamp, opcjonalnie eventId/user
    private final Map<String, Object> map = new ConcurrentHashMap<>()

    void put(String key, Object value) {
        map.put(key, value)
    }

    Object get(String key) {
        return map.get(key)
    }

    Map<String, Object> snapshot() {
        return new HashMap<>(map)
    }
}
```

w metodach z @Async dodaj coś takiego:
przykładowo w GlobalStatsProjector.groovy:
```groovy
@Async("bulkTaskExecutor") // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
@EventListener
@Transactional
void projectBatchToGlobalSummary(TransactionBatchProcessedEvent event) {
   log.info(">>> [CQRS-PROJECTOR] Aktualizuję widok globalny dla: {}", event.userName)

   // zapisujemy, który wątek obsługuje ostatni event
   // klucz zawiera nazwę bean'a/metody, by nie nadpisywać innych projektorów
   threadTracker.put('GlobalStatsProjector.lastThread', [thread: Thread.currentThread().name,
                                                         ts: System.currentTimeMillis(),
                                                         user: userName,
                                                         count: transactionsCount])
```

przykładowo w SmartFinFacade.groovy:
```groovy
@Async("bulkTaskExecutor") // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
void processInBackgroundTask(String userName, List<Transaction> rawTransactions, List<String> rules) {
   log.info(">>> [ASYNC] Rozpoczynam ciężką pracę w tle dla: {}", userName)

        // Zapisujemy informacje o wątku/ts i liczbie transakcji — przydatne w testach i diagnostyce
        threadTracker.put('SmartFinFacade.processInBackgroundTask', [
                thread: Thread.currentThread().name,
                ts: System.currentTimeMillis(),
                user: userName,
                count: rawTransactions.size()
        ])
```

przykładowo w AsyncNotificationService
```groovy
@Async("bulkTaskExecutor") // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
@EventListener
void onBatchProcessed(TransactionBatchProcessedEvent event) {
    log.info(">>> [AUDIT] Użytkownik {} właśnie zaimportował {} transakcji.",
            event.userName, event.transactionsCount)
    // Zapisujemy informacje o wątku i czasie dla celów debugowania/monitoringu
    threadTracker.put('AuditEventListener.onBatchProcessed', [thread: Thread.currentThread().name,
                                                              ts: System.currentTimeMillis(),
                                                              user: event?.userName,
                                                              count: event?.transactionsCount])
    [...]
```

przykładowo w AuditEventListener
```groovy
@Async("bulkTaskExecutor") // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
@EventListener
void onBatchProcessed(TransactionBatchProcessedEvent event) {
    log.info(">>> [AUDIT] Użytkownik {} właśnie zaimportował {} transakcji.",
            event.userName, event.transactionsCount)
    // Zapisujemy informacje o wątku i czasie dla celów debugowania/monitoringu
    threadTracker.put('AuditEventListener.onBatchProcessed', [thread: Thread.currentThread().name,
                                                              ts: System.currentTimeMillis(),
                                                              user: event?.userName,
                                                              count: event?.transactionsCount])
```

To rozwiązanie od ChatGPT jest bardzo dobre – wprowadza pojęcie Observability (obserwowalności). 
Jako Mid-developer musisz wiedzieć, że w systemach asynchronicznych logi w konsoli to za mało. 
Potrzebujesz "punktu kontrolnego", do którego możesz zajrzeć, żeby sprawdzić, czy zadania w tle nie "wiszą" i na jakich wątkach pracują.

Zrealizujmy teraz Twój plan: 
stworzymy test sprawdzający tożsamość wątków oraz endpoint REST, który pozwoli Ci podglądać te dane "na żywo".

Krok 1: Rozbudowa Kontrolera (MonitoringController.groovy)
----------------------------------------------------------
Stworzymy dedykowany endpoint dla administratora, który pozwoli podejrzeć aktualny stan ThreadTrackera. 
Użyjemy do tego mapowania, które już znasz.

Stwórz plik `src/main/groovy/pl/edu/praktyki/web/MonitoringController.groovy`

```groovy
package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pl.edu.praktyki.service.ThreadTracker

@RestController
@RequestMapping("/api/admin/monitoring")
class MonitoringController {

    @Autowired
    ThreadTracker threadTracker

    @GetMapping("/threads")
    @PreAuthorize("hasRole('ADMIN')") // Bezpieczeństwo przede wszystkim!
    Map<String, Object> getAsyncThreadStats() {
        // Zwracamy zrzut pamięci trackera
        return [
            systemTime: java.time.LocalDateTime.now().toString(),
            activeTasksInfo: threadTracker.snapshot()
        ]
    }
}
```

Krok-2: Test Spock – Weryfikacja Puli Wątków (AsyncMonitoringSpec.groovy)
-------------------------------------------------------------------------
To jest test, który udowodni, że Twoja konfiguracja z Lab 84 (prefiks bulkTaskExecutorZapierdala--) faktycznie działa.

   pamiętasz:  w klasie AsyncConfig
        // 4. Prefiks: Żebyś w logach widział dokładnie, kto "zapierdala"
        executor.setThreadNamePrefix("bulkTaskExecutorZapierdala--")

```groovy
    @Bean(name = "bulkTaskExecutor")
    Executor smartFinExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor()
        
        // 1. Rdzeń: Ile wątków zawsze czeka na pracę?
        executor.corePoolSize = 3
        
        // 2. Kolejka: Jeśli kucharze są zajęci, ile zamówień może czekać w kolejce?
        executor.queueCapacity = 100
        
        // 3. Max: Jeśli kolejka jest pełna, do ilu wątków możemy dobić?
        executor.maxPoolSize = 10
        
        // 4. Prefiks: Żebyś w logach widział dokładnie, kto "zapierdala"
        executor.setThreadNamePrefix("bulkTaskExecutorZapierdala--")

        // 5. Zawór bezpieczeństwa (Backpressure): 
        // Co jeśli kucharze zajęci I kolejka pełna? 
        // CallerRunsPolicy mówi: "Wątek, który to wysłał, musi to sam zrobić". 
        // To naturalnie spowalnia napływ danych!
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())
        
        executor.initialize()
        return executor
    }
```

Stwórz więc ten test: `src/test/groovy/pl/edu/praktyki/async/AsyncMonitoringSpec.groovy`

```groovy
package pl.edu.praktyki.async

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import pl.edu.praktyki.service.ThreadTracker

import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class AsyncMonitoringSpec extends BaseIntegrationSpec {

   @Autowired ApplicationEventPublisher eventPublisher
   @Autowired ThreadTracker threadTracker

   def "powinien zarejestrować informację o wątku z poprawnej puli w ThreadTrackerze"() {
      given: "zdarzenie przetwarzania paczki"
      def event = new TransactionBatchProcessedEvent(
              userName: "MonitorTester",
              totalBalance: 100.0,
              transactionsCount: 5
      )

      when: "publikujemy zdarzenie, które uruchamia asynchronicznego słuchacza"
      eventPublisher.publishEvent(event)

      then: "Czekamy asynchronicznie, aż tracker zostanie uzupełniony"
      await().atMost(5, TimeUnit.SECONDS).until {
         // POPRAWKA KLUCZA: musi być identyczny jak w GlobalStatsProjector
         threadTracker.get('GlobalStatsProjector.lastThread') != null
      }

      and: "wyciągamy dane z trackera"
      Map stats = threadTracker.get('GlobalStatsProjector.lastThread') as Map

      then: "wątek musi mieć Twój nowy prefiks z logów"
      // Zmień to na to, co faktycznie masz w AsyncConfig!
      stats.thread.startsWith("bulkTaskExecutorZapierdala--")
      stats.user == "MonitorTester"

      println ">>> SUKCES! Znaleziono dane dla wątku: ${stats.thread}"

      and: "możemy wypisać statystyki dla pewności"
      println ">>> Przetworzono na wątku: ${stats.thread} o czasie: ${stats.ts}"
   }
}
```

Krok-3: Test Spock integracyjny dla klasy AsyncNotificationService
------------------------------------------------------------------
Ten test jest o tyle ciekawy, że musi zweryfikować trzy aspekty typowe dla poziomu Mid/Senior:

Asynchroniczność: 
Czy test idzie dalej, nie czekając 6 sekund na sleep?

Event-Driven: 
Czy Spring poprawnie przekazał zdarzenie do słuchacza?

Observability: 
Czy `ThreadTracker` poprawnie zapisał dane o "pracowniku" (wątku)?

Stwórz plik w `src/test/groovy/pl/edu/praktyki/service/AsyncNotificationSpec.groovy`

```groovy
package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import java.time.Duration
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

class AsyncNotificationSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired AsyncNotificationService notificationService
    @Autowired ThreadTracker threadTracker

    def "powinien przetworzyć powiadomienie asynchronicznie w osobnym wątku"() {
        given: "czysty stan licznika"
        notificationService.reset()
        def event = new TransactionBatchProcessedEvent(
                userName: "AsyncTester",
                totalBalance: 1500.0,
                transactionsCount: 10L,
                generatedReport: "Test Report Content"
        )

        when: "publikujemy zdarzenie"
        long startTime = System.currentTimeMillis()
        eventPublisher.publishEvent(event)
        long publishTime = System.currentTimeMillis() - startTime

        then: "1. Publikacja musi być natychmiastowa (nie blokuje jej sleep 6s)"
        // Gdyby metoda była synchroniczna, ten test trwałby min. 6 sekund
        publishTime < 1000 

        and: "2. W tym momencie licznik wciąż powinien być na 0"
        notificationService.getProcessedCount() == 0

        then: "3. Czekamy asynchronicznie (max 15s), aż praca w tle się zakończy"
        await().atMost(15, TimeUnit.SECONDS).until {
            notificationService.getProcessedCount() == 1
        }

        and: "4. Weryfikujemy dane zapisane w ThreadTrackerze"
        Map stats = threadTracker.get('AsyncNotificationService.handleBatchEvent') as Map
        
        stats != null
        stats.user == "AsyncTester"
        stats.count == 10L
        
        // Sprawdzamy, czy wątek ma poprawny prefiks z Twojego AsyncConfig
        // (W logach pisałeś, że masz: bulkTaskExecutorZapierdala--)
        stats.thread.startsWith("bulkTaskExecutorZapierdala--")

        and: "wyświetlamy diagnostykę"
        println ">>> Test zakończony sukcesem."
        println ">>> Zadanie wykonane przez wątek: ${stats.thread}"
        println ">>> Czas trwania publikacji (synchronicznie): ${publishTime}ms"
    }
}
```

Dlaczego ten test jest "po inżyniersku"? (Analiza)

Testowanie braku blokowania: 
Sprawdzamy publishTime < 1000. 
To kluczowy dowód na to, że `@Async` działa. 
Jeśli usunąłbyś tę adnotację z serwisu, ten test natychmiast padnie, bo system będzie czekał 6 sekund na sleep przed powrotem do testu.

Inteligentne czekanie (Awaitility): 
Ustawiamy atMost(15, SECONDS), ponieważ Twój sleep trwa 6 sekund. 
Dajemy Springowi bezpieczny margines na przełączenie kontekstów.

Weryfikacja Kontraktu Monitoringu: 
Sprawdzamy, czy klucz w threadTracker zgadza się dokładnie z tym, co wpisałeś w serwisie (AsyncNotificationService.handleBatchEvent). 
To uczy dbania o spójność "magicznych stringów" w systemie.

Referencja przez getter: 
Użyliśmy notificationService.getProcessedCount(), co (jak już wiesz) jest niezbędne, 
aby przebić się przez Proxy Springa do prawdziwej wartości AtomicInteger.

Co musisz zrobić:
-----------------
1. Upewnij się, że masz w build.gradle bibliotekę awaitility.
2. Uruchom ten test.
3. Obserwuj logi: 
   Zobaczysz, jak test melduje sukces, a potem po 6 sekundach 
   zobaczysz w konsoli log [ASYNCHRONICZNY-EVENT] Raport o bilansie 1500.00 PLN został pomyślnie przetworzony....

To jest idealny test do pokazania w portfolio. 
Pokazuje, że rozumiesz asynchroniczność i umiesz ją zmierzyć. Daj znać, czy przeszedł!  


Krok-4: Test Spock integracyjny dla klasy SmartFinFacade (metoda: processInBackgroundTask)
------------------------------------------------------------------------------------------
Metoda `processInBackgroundTask` w `SmartFinFacade` pełni rolę orkiestratora. 
Test musi udowodnić, że wywołanie tej metody nie blokuje głównego wątku oraz że dane diagnostyczne trafiają do `ThreadTrackera`.

Stwórz plik w `src/test/groovy/pl/edu/praktyki/facade/SmartFinFacadeAsyncSpec.groovy`

```groovy
package pl.edu.praktyki.facade

import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.service.ThreadTracker
import java.time.LocalDate
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

class SmartFinFacadeAsyncSpec extends BaseIntegrationSpec {

    @Autowired SmartFinFacade facade
    @Autowired ThreadTracker threadTracker

    def "powinien uruchomić ciężkie przetwarzanie w tle i zarejestrować wątek z puli bulk"() {
        given: "przygotowane dane wejściowe"
        def user = "FacadeAsyncTester"
        def transactions = [
                new Transaction(id: "T-ASYNC-1", amount: 1000.0, category: "Test", date: LocalDate.now())
        ]
        def rules = ["addTag('PROCESSED_IN_BACKGROUND')"]

        when: "wywołujemy metodę fasady (powinna wrócić natychmiast dzięki @Async)"
        long startTime = System.currentTimeMillis()
        facade.processInBackgroundTask(user, transactions, rules)
        long duration = System.currentTimeMillis() - startTime

        then: "1. Wywołanie nie zablokowało testu (trwało ułamek sekundy)"
        duration < 1000

        then: "2. Czekamy asynchronicznie, aż wątek tła zapisze się w ThreadTrackerze"
        await().atMost(10, TimeUnit.SECONDS).until {
            threadTracker.get('SmartFinFacade.processInBackgroundTask') != null
        }

        and: "3. Weryfikujemy metadane zapisane przez wątek asynchroniczny"
        Map stats = threadTracker.get('SmartFinFacade.processInBackgroundTask') as Map
        
        stats.thread != null
        // Sprawdzamy czy prefiks wątku zgadza się z Twoją konfiguracją w AsyncConfig
        stats.thread.startsWith("bulkTaskExecutorZapierdala--")
        
        and: "wyświetlamy diagnostykę wątków"
        println "----------------------------------------------------------------"
        println "METRYKI ASYNCHRONICZNE FASADY:"
        println "Uruchomiono przez wątek: ${Thread.currentThread().name} (powinien być Test worker)"
        println "Przetworzono w tle przez: ${stats.thread}"
        println "Czas odpowiedzi API: ${duration}ms"
        println "----------------------------------------------------------------"
    }
}
```

Dlaczego ten test jest "Middowy"? (Analiza)

Weryfikacja Puli Wątków: 
Sprawdzasz `stats.thread.startsWith("bulkTaskExecutorZapierdala--")`. 
To dowodzi, że rozumiesz, iż `@Async` bez podania nazwy puli mógłby użyć domyślnego (często niewydajnego) executora. 
Ty wymuszasz użycie swojej zoptymalizowanej puli.

Oddzielenie Wątku Wywołującego: 
Test udowadnia, że wątek o nazwie `Test worker` (główny wątek testu) tylko "zlecił" robotę, 
      a robotę wykonał executor `bulkTaskExecutorZapierdala--1`.

Awaitility vs Sleep: 
Ponownie używamy inteligentnego czekania. 
Gdyby metoda `processAndGenerateReport` trwała 5 sekund, test nie wywali się, tylko będzie cierpliwie odpytywał `ThreadTrackera`.

Odpal ten test i sprawdź logi diagnostyczne, które dopisałem w sekcji and:. 
Powinieneś zobaczyć tam czarno na białym, jak te dwa wątki ze sobą współpracują! 



Krok-5: Test Spock integracyjny dla klasy AuditEventListenerSpec (metoda: onBatchProcessed)
-------------------------------------------------------------------------------------------
Ten test udowadnia jedną z najważniejszych cech systemów Event-Driven: to, że komponenty są "luźno powiązane" (Loose Coupling). 
Test nie wywołuje metody `onBatchProcessed(...)` ręcznie – on po prostu wrzuca zdarzenie do "magistrali" Springa i sprawdza, 
czy odpowiedni pracownik (wątek) podniósł słuchawkę.

Stwórz plik w `src/test/groovy/pl/edu/praktyki/service/AuditEventListenerSpec.groovy`

```groovy
package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

class AuditEventListenerSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired ThreadTracker threadTracker

    def "powinien asynchronicznie zarejestrować audyt po otrzymaniu zdarzenia o przetworzonej paczce"() {
        given: "zdarzenie o zakończeniu przetwarzania paczki"
        def event = new TransactionBatchProcessedEvent(
                userName: "AuditUser",
                totalBalance: 2500.0,
                transactionsCount: 15L,
                generatedReport: "Pełny raport audytowy..."
        )

        when: "publikujemy zdarzenie w systemie"
        // Nie wołamy listenera bezpośrednio! Udajemy, że Fasada skończyła pracę.
        eventPublisher.publishEvent(event)

        then: "Czekamy asynchronicznie, aż AuditEventListener zapisze dane w ThreadTrackerze"
        await().atMost(5, TimeUnit.SECONDS).until {
            threadTracker.get('AuditEventListener.onBatchProcessed') != null
        }

        and: "dane zapisane w trackerze są poprawne"
        Map stats = threadTracker.get('AuditEventListener.onBatchProcessed') as Map
        
        stats.user == "AuditUser"
        stats.count == 15L
        
        and: "operacja została wykonana na wątku z dedykowanej puli"
        // Weryfikujemy czy @Async("bulkTaskExecutor") zadziałało
        stats.thread.startsWith("bulkTaskExecutorZapierdala--")

        and: "wyświetlamy potwierdzenie w konsoli"
        println ">>> [AUDIT-TEST] Zdarzenie przechwycone przez wątek: ${stats.thread}"
        println ">>> [AUDIT-TEST] Dane użytkownika w audycie: ${stats.user}"
    }
}
```

Dlaczego ten test to "Pure Gold" dla Twojego portfolio?

Testowanie wzorca Pub/Sub: 
Nie testujesz metody `onBatchProcessed(...)` jak zwykłej funkcji. 
Testujesz mechanizm dostarczania wiadomości. 
To pokazuje rekruterowi, że rozumiesz architekturę zdarzeń.

Inżynierska precyzja: 
Sprawdzasz `stats.thread.startsWith("bulkTaskExecutorZapierdala--")`. 
To dowód na to, że kontrolujesz, gdzie fizycznie wykonuje się kod (nie pozwalasz Springowi używać domyślnych, słabych wątków).

Wiedza o infrastrukturze: 
Użycie `ApplicationEventPublisher` pokazuje, że wiesz, jak Spring zarządza komunikacją 
między komponentami bez ich bezpośredniego importowania (tzw. `Inversion of Control`).

Co się wydarzy po uruchomieniu?
-------------------------------
1. Główny wątek testu wyśle wiadomość i pójdzie dalej.
2. Spring w tle znajdzie `AuditEventListener`.
3. Pula `bulkTaskExecutor` przydzieli wolny wątek (np. `bulkTaskExecutorZapierdala--n`).  n - wybierany losowo:  1, 2, 3, ...
4. Wątek ten wykona `threadTracker.put(...)`.
5. Awaitility w teście co 100ms sprawdza tracker, aż w końcu "zobaczy" te dane i zakończy test sukcesem.

Uruchom test i ciesz się widokiem asynchronicznego audytu! 
Jeśli to przejdzie, Twój system asynchroniczny jest w 100% przetestowany i gotowy na każde wyzwanie. 





Krok-5: Jak to sprawdzić "produkcyjnie"?
----------------------------------------
1. Upewnij się, że w metodach `@Async` (np. w `GlobalStatsProjector`) masz ten kod `threadTracker.put(...)`.
2. Uruchom aplikację: `  ./gradlew runSmartFinDb -PappArgs="-u Jacek -f transactions-duza-ilosc.csv"  `
3. Zaloguj się w Postmanie jako Admin (pobierz token JWT).
4. Wyślij GET do: http://localhost:8080/api/admin/monitoring/threads

Co zobaczysz w JSON-ie?
Dostaniesz raport, który powie Ci: 
"Ostatni raport finansowy był generowany przez wątek FinWorker-1 dla użytkownika Jacek o godzinie X".

Dlaczego to jest "Mid-level Mastery"?

Observability Pattern: 
Pokazujesz, że nie ufasz "magii". 
Budujesz narzędzia, które pozwalają Ci zajrzeć do wnętrza działającego systemu.

Thread Safety: 
Używając `ConcurrentHashMap` w `ThreadTracker`, udowadniasz, że rozumiesz, iż wiele wątków naraz będzie próbowało tam pisać, 
i zabezpieczyłeś się przed uszkodzeniem danych.

Audit & Debugging: 
Ten mechanizm pozwala na produkcyjne śledzenie "kto ostatnio co robił w tle". 
Gdyby system zaczął działać wolno, Twój endpoint pokazałby np. że wszystkie zadania wykonuje tylko jeden wątek, 
co sugerowałoby błąd w konfiguracji puli.

Zadanie dla Ciebie:
-------------------
1. Wdroż MonitoringController.
2. Uruchom test `AsyncMonitoringSpec`.
3. Sprawdź, czy Twoje println w teście pokazało prefiks `bulkTaskExecutorZapierdala--`.


Kolejny krok: Lab 86: Obsługa wyjątków w @Async (AsyncUncaughtExceptionHandler) – bo co zrobisz, gdy wątek w tle wybuchnie błędem? System musi o tym wiedzieć! 🏎️💨💥


