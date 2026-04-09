# App: Smart-Fin-Analyzer


uruchamianie aplikacji i testów:
--------------------------------
patrz tutaj: C:\dev\smart-fin-analyzer\scripts\Readme--Uruchamianie-aplikacji-i-testów.md
lub tutaj: C:\dev\smart-fin-analyzer\scripts\README.md    (to jest wygenerowane przez AI)
-----------------------------------------------------------------------------------------


OPIS PROJEKTU:
--------------
A modern, multi-threaded, polyglot (Java/Groovy) CLI application for financial transaction analysis, built on top of **Spring Boot 3**.

## 📖 Overview
Smart-Fin-Analyzer is an enterprise-grade command-line tool designed to ingest, process, and analyze financial data. It demonstrates the powerful synergy between the Java ecosystem (stability, Spring Framework) and Groovy (expressiveness, dynamic rules, fast I/O).

## 🛠️ Tech Stack
* **Core:** Java 17, Groovy 4.0
* **Framework:** Spring Boot 3.2, Spring Data JPA
* **Concurrency:** GPars (Groovy Parallel Systems)
* **Database:** H2 (File-based local storage)
* **Testing:** Spock Framework 2.3 (BDD)
* **Build Tool:** Gradle 8.x (with Toolchains)
* **Integration:** Java `HttpClient` (REST API integration)

## ✨ Key Features
1. **Multi-threaded Ingestion:** Utilizes `GParsPool` to process large batches of transactions across multiple CPU cores simultaneously.
2. **Dynamic Rule Engine:** Employs `GroovyShell` with a `SecureASTCustomizer` sandbox to apply user-defined business rules (e.g., tagging transactions) safely at runtime.
3. **Live Currency Conversion:** Integrates with an external REST API using native Java 11+ `HttpClient` to normalize multi-currency transactions into a base currency (PLN).
4. **Data Persistence:** Uses Hibernate/JPA to store historical transactions, clearly separating the Domain Model (POGO) from the Database Entities.
5. **CLI Orchestration:** Implements Groovy's `CliBuilder` (via Picocli) for a professional terminal user experience (flags, default values, error handling).
6. **Template Reporting:** Generates formatted financial summaries using Groovy's `SimpleTemplateEngine`.

 
 

## ✨ Architecture Highlights
-----------------------------
1. **Clean Architecture: Strict separation between the Transaction domain object and TransactionEntity JPA object.
2. **Traits: Utilizes Groovy Traits for composable, cross-cutting concerns (e.g., AuditLog).
3. **DSL & Metaprogramming: Custom Object Graph Builders and closures for highly readable data manipulation.

## 🔌 PluginManager
The `PluginManager` class (`pl.edu.praktyki.utils.PluginManager`) is a lightweight, closure-based plugin system 
that leverages Groovy's first-class support for closures.

**How it works:**
- Plugins are registered as Groovy `Closure` objects via `addPlugin(Closure)` and stored in a private list.
- Calling `runAll(Object data)` executes every registered plugin sequentially, passing the same `data` object to each one.

**Example usage:**
```groovy
def manager = new PluginManager()

manager.addPlugin { tx -> println "Audit: ${tx.id}" }
manager.addPlugin { tx -> if (tx.amount < 0) tx.addTag('EXPENSE') }

manager.runAll(myTransaction)
```

This pattern enables a dynamic, extensible pipeline — 
   new processing steps can be added at runtime without modifying existing code, following the **Open/Closed Principle**.



## 🧩 Architektura i Wzorce Projektowe
System został zaprojektowany z wykorzystaniem najlepszych praktyk inżynierskich:
* **Chain of Responsibility:** Logika walidacji transakcji (Fraud Detection) rozbita na niezależne jednostki.
* **Strategy Pattern:** Dynamiczne wybieranie logiki procesowania (`VipOrderStrategy`, `StandardOrderStrategy`).
* **Proxy / AOP:** Przechwytywanie wywołań metod dla celów logowania czasu i bezpieczeństwa.
* **Facade:** Uproszczony interfejs (`SmartFinFacade`) ukrywający złożoność całego systemu.
* **Composite:** Rekurencyjne drzewa danych (Portfele inwestycyjne) z zachowaniem zasady Liskov.
* **Singleton (Spring Scope):** Świadome zarządzanie stanem aplikacji w środowisku wielowątkowym.
  Czy to wyczerpuje temat?

TAK. Jeśli rozumiesz te wzorce i potrafisz je zaimplementować w Groovym, to jesteś na poziomie,
który pozwala Ci wejść do dowolnego projektu opartego na JVM i szybko połapać się w jego architekturze.

## 🧪 Zaawansowana Architektura Testów (Dual-Profile)
Projekt wykorzystuje profesjonalne podejście do testów integracyjnych, oddzielając środowisko CI/CD 
od środowiska deweloperskiego za pomocą profili Spring:
* **Profil domyślny (`Testcontainers`):** Automatycznie podnosi ulotny kontener PostgreSQL w Dockerze. 
* Gwarantuje to 100% zgodności z produkcją i bezobsługowe działanie na serwerach CI (Jenkins/GitLab CI).
* **Profil `local-pg`:** Przeznaczony do głębokiego debugowania. Łączy się z lokalną instancją PostgreSQL, 
* co pozwala na inspekcję danych "na żywo" przy użyciu narzędzi takich jak DBeaver w trakcie wstrzymania (breakpoint) testu.



//////////////////   Asynchroniczność i Eventy - Przetwarzanie Batchowe (Background Jobs)   //////////////////
--------------------------------------------------------------------------------------------------------------
Słuchaj, to nie jest „pojbany” opis, tylko po prostu techniczny bełkot. Spróbujmy to napisać tak, żebyś za miesiąc zajrzał do tego pliku i od razu wiedział, o co chodziło, bez drapania się po głowie.

Oto uproszczona i przejrzysta wersja do Twojego README.md:

⚡ Asynchroniczność w Springu: Jak to działa?

W tym projekcie używamy adnotacji @Async, aby ciężkie zadania (jak wysyłanie raportów) działy się „w tle”, nie blokując użytkownika.

1. Magiczne "Opakowanie" (Proxy)

Kiedy dodajesz @Async nad metodą w klasie oznaczonej jako @Service lub @Component, Spring nie uruchamia tej klasy bezpośrednio.

Tworzy „Asystenta” (Proxy): Spring owija Twój serwis w niewidzialne opakowanie.

Przechwytuje wywołanie: Gdy wywołujesz metodę, trafiasz najpierw do „Asystenta”.

Deleguje pracę: Asystent mówi: „Dobra, ja to ogarnę w innym wątku, a ty leć dalej”. Sam wykonuje Twoją metodę na zapleczu.

Ważne: To działa tylko jeśli wywołujesz metodę z innej klasy. Jeśli metoda wewnątrz tej samej klasy wywoła inną swoją metodę z @Async, magia nie zadziała (bo ominiesz „Asystenta”).

2. Konfiguracja "Ekipy Wykonawczej" (Thread Pool)

Aby mieć kontrolę nad tym, ile wątków na raz mieli dane, skonfigurowaliśmy własną pulę wątków o nazwie bulkTaskExecutor (w pliku AsyncConfig.groovy).

Co oznaczają parametry w kodzie?

setCorePoolSize(5): Twoja stała ekipa. 5 wątków zawsze czeka w gotowości.

setQueueCapacity(100): Poczekalnia. Jeśli stała ekipa jest zajęta, 100 kolejnych zadań może czekać w kolejce.

setMaxPoolSize(10): Posiłki. Jeśli ekipa jest zajęta, a poczekalnia (kolejka) jest pełna, Spring zatrudni dodatkowe osoby (max do 10).

setThreadNamePrefix("bulk-"): Identyfikator. W logach zobaczysz nazwę wątku bulk-1, bulk-2, dzięki czemu od razu wiesz, że to zadanie z tej konkretnej puli.

3. Jak używać @Async w kodzie?
   code
   Groovy
   download
   content_copy
   expand_less
   @Async("bulkTaskExecutor") // Mówię Springowi: "Zrób to w tle, używając ekipy 'bulk'"
   void myHeavyMethod() {
   // ciężka praca...
   }

O czym musisz pamiętać?

Musisz mieć @EnableAsync w pliku konfiguracyjnym.

Klasa musi być Beanem Springa (np. mieć @Service).

Jeśli w @Async("nazwa") podasz złą nazwę (taką, której nie ma w AsyncConfig), Spring wywali błąd przy starcie. Jeśli nie podasz nazwy w ogóle (@Async), użyje domyślnego, bardzo prostego mechanizmu, który nie zawsze jest wydajny.

Dlaczego to jest lepsze dla Ciebie?

Używasz porównań (Asystent, Ekipa, Poczekalnia).

Masz jasne punkty (Co robić, a czego nie).

Wygląda to profesjonalnie w portfolio – rekruter zobaczy, że rozumiesz różnicę między "wątkiem" a "pulą wątków".

Wklej to sobie i daj znać, czy teraz ta "asynchroniczna magia" jest dla Ciebie jaśniejsza! 🚀


///////////////////////////////////////////////////////////////



moje pytanie:
-------------

a w tym przypadku, jak mam klasę typu @Service ale metoda jest  @EventListener   ??:

```groovy
@Service
@Slf4j
class AsyncNotificationService {

 
    private final AtomicInteger processedEventsCount = new AtomicInteger(0)

    // Dodajemy metodę - dzięki niej Proxy będzie wiedziało, skąd wziąć wartość
    int getProcessedCount() {
        return processedEventsCount.get()
    }

 
    @Async("bulkTaskExecutor")  
    @EventListener
    void handleBatchEvent(TransactionBatchProcessedEvent event) {

        log.info(">>> [ASYNCHRONICZNY-EVENT] Rozpoczynam wysyłkę raportu do systemu zewnętrznego dla: {}", event.userName)

        // Symulujemy ciężką pracę (np. generowanie PDF i wysyłka maila)
        sleep(2000)

        log.info(">>> [ASYNCHRONICZNY-EVENT] Raport o bilansie {} PLN został pomyślnie przetworzony w tle.", event.totalBalance)

        processedEventsCount.incrementAndGet()
    }

    // Opcjonalnie metoda do resetowania licznika między testami
    void reset() {
        processedEventsCount.set(0)
    }

}
```

Odpowiedź:
----------


To świetne pytanie! Ten przypadek to wyższy stopień wtajemniczenia, bo łączymy dwa różne mechanizmy Springa: Zdarzenia (Events) oraz Asynchroniczność.

Oto jak wytłumaczyć to "po ludzku" do Twojego README, aby wyjaśnić tę konkretną klasę:

📡 Architektura Reaktywna: @EventListener + @Async

W klasie AsyncNotificationService zastosowaliśmy potężne połączenie dwóch adnotacji. To sprawia, że nasz system jest „rozproszony wewnątrz” i nie blokuje użytkownika.

1. Jak to działa? (Metafora Listonosza i Biura)

Wyobraź sobie, że Twoja aplikacja to wielkie biuro:

Fasada (nadawca): Kończy pracę i krzyczy na całe biuro: "Ej! Właśnie przetworzyłem nową paczkę transakcji!" (to jest wywołanie publishEvent).

Listonosz (Spring Event Bus): Słyszy to i sprawdza, kto w biurze czeka na taką informację.

@EventListener (odbiorca): Adnotacja nad metodą handleBatchEvent działa jak antena. Mówi Springowi: "Ja! Ja chcę o tym wiedzieć! Za każdym razem, gdy padnie takie hasło, uruchom mnie".

2. Rola @Async w tym procesie

Gdybyśmy mieli samo @EventListener, biuro by stanęło. Fasada musiałaby czekać, aż odbiorca (NotificationService) skończy wysyłać maile (co trwa u nas 2 sekundy).

Dzięki dodaniu @Async("bulkTaskExecutor"):

Fasada krzyczy o zdarzeniu i natychmiast wraca do swoich spraw (oddaje raport użytkownikowi).

Listonosz przekazuje informację do AsyncNotificationService.

Serwis ten mówi: "Dzięki! Nie róbmy tego tutaj. Przekażcie to do mojej ekipy pomocniczej bulkTaskExecutor".

Jeden z wolnych wątków z puli "bulk" bierze to zadanie na warsztat i przez 2 sekundy wysyła raport w tle.

3. Dlaczego używamy AtomicInteger?

Ponieważ metoda działa w tle na różnych wątkach, zwykły licznik (int) mógłby się "pogubić" (dwa wątki mogłyby spróbować dodać 1 w tym samym ułamku sekundy i jeden wynik by przepadł).
AtomicInteger to taki specjalny, "pancerny" licznik, który gwarantuje, że każde dodanie 1 zostanie poprawnie zapisane, nawet jeśli 10 wątków rzuci się na niego naraz.

4. Dlaczego mamy metodę getProcessedCount()?

Pamiętasz "Asystenta" (Proxy)?
W teście Spocka, kiedy pytasz o stan licznika, rozmawiasz z Asystentem.

Jeśli pole byłoby publiczne, Asystent mógłby pokazać Ci swoją starą wersję (zero).

Gdy pytasz przez metodę, Asystent musi "wejść do środka" prawdziwego serwisu, sprawdzić stan AtomicInteger i podać Ci prawdziwą, aktualną wartość.

Podsumowanie do zapamiętania (Short version):

@EventListener – sprawia, że metoda "słucha" komunikatów w systemie.

@Async – sprawia, że to słuchanie odbywa się na boku, nie opóźniając głównego programu.

To połączenie pozwala budować systemy, które są błyskawiczne dla użytkownika, bo wszystkie "ciężkie" i "nudne" rzeczy (maile, logi, audyt) dzieją się po cichu na zapleczu.

Czy teraz ta "reaktywna" część Twojego serwisu jest już jasna? 🚀






