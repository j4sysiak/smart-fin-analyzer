# Smart-Fin-Analyzer


## Uruchamianie aplikacji i testów

- główna instrukcja: `scripts/Readme--Uruchamianie-aplikacji-i-testów.md`
- dodatkowy plik pomocniczy: `scripts/README.md`

## Smoke check audytu po uploadzie CSV

Po uploadzie przez `POST /api/transactions/upload?user=admin` warto szybko potwierdzić, że zapis trafił zarówno do tabeli biznesowej, jak i do audytu.

Najprostszy check:
- użyj w CSV unikalnych `original_id`, np. `MANUAL-AUD-001` i `MANUAL-AUD-002`,
- sprawdź, czy rekordy są w `transactions`,
- sprawdź, czy dla tych samych `original_id` są wpisy w `transactions_aud`,
- sprawdź, czy `revtype = 0` (insert),
- opcjonalnie potwierdź, że rewizje z `transactions_aud.rev` istnieją też w `revinfo`.

Pełne komendy SQL / `psql` są w `scripts/Readme--Uruchamianie-aplikacji-i-testów.md`, sekcja `Manual audit smoke check po uploadzie CSV`.


## Opis projektu

Smart-Fin-Analyzer to nowoczesna aplikacja CLI do importu, przetwarzania i analizy transakcji finansowych, zbudowana w oparciu o **Spring Boot 3** oraz ekosystem **Java/Groovy**. Projekt łączy stabilność Javy i Springa z ekspresyjnością Groovy'ego, dynamicznymi regułami biznesowymi oraz wygodnym I/O.

## 🛠️ Stos technologiczny
* **Core:** Java 17, Groovy 4.0
* **Framework:** Spring Boot 3.2, Spring Data JPA
* **Concurrency:** GPars (Groovy Parallel Systems)
* **Database:** PostgreSQL
* **Testing:** Spock Framework 2.3 (BDD), profile `tc` / `local-pg` na PostgreSQL
* **Build Tool:** Gradle 8.x (with Toolchains)
* **Integration:** Java `HttpClient` (REST API integration)

## ✨ Kluczowe funkcje
1. **Wielowątkowy import:** równoległe przetwarzanie dużych paczek transakcji przez `GParsPool`.
2. **Dynamiczne reguły biznesowe:** `GroovyShell` z `SecureASTCustomizer` do bezpiecznego wykonywania reguł w runtime.
3. **Przeliczanie walut:** integracja z zewnętrznym REST API przez natywne `HttpClient`.
4. **Trwałość danych:** Hibernate/JPA i czytelny rozdział modelu domenowego od encji bazodanowych.
5. **CLI i raportowanie:** `CliBuilder` / Picocli oraz `SimpleTemplateEngine`.

## ✨ Najważniejsze elementy architektury

1. **Clean Architecture:** ścisły rozdział między obiektem domenowym `Transaction` a encją JPA `TransactionEntity`.
2. **Traits:** wykorzystanie Groovy Traits do realizacji przekrojowych odpowiedzialności, takich jak logowanie audytowe.
3. **DSL i metaprogramowanie:** własne buildery obiektów oraz closures do czytelnej manipulacji danymi.

## 🔌 PluginManager
Klasa `PluginManager` (`pl.edu.praktyki.utils.PluginManager`) jest lekkim, opartym o closures systemem pluginów wykorzystującym natywne wsparcie Groovy'ego dla funkcji wyższego rzędu.

**Jak działa:**
- pluginy są rejestrowane przez `addPlugin(Closure)` i przechowywane na prywatnej liście,
- `runAll(Object data)` uruchamia je sekwencyjnie dla tego samego obiektu `data`.

**Przykład użycia:**
```groovy
def manager = new PluginManager()

manager.addPlugin { tx -> println "Audit: ${tx.id}" }
manager.addPlugin { tx -> if (tx.amount < 0) tx.addTag('EXPENSE') }

manager.runAll(myTransaction)
```

Ten wzorzec pozwala budować rozszerzalny pipeline przetwarzania, w którym nowe kroki można dodawać w runtime bez modyfikowania istniejącego kodu, zgodnie z zasadą **Open/Closed Principle**.



## 🧩 Architektura i Wzorce Projektowe
System został zaprojektowany z wykorzystaniem najlepszych praktyk inżynierskich:
* **Chain of Responsibility:** Logika walidacji transakcji (Fraud Detection) rozbita na niezależne jednostki.
* **Strategy Pattern:** Dynamiczne wybieranie logiki procesowania (`VipOrderStrategy`, `StandardOrderStrategy`).
* **Proxy / AOP:** Przechwytywanie wywołań metod dla celów logowania czasu i bezpieczeństwa.
* **Facade:** Uproszczony interfejs (`SmartFinFacade`) ukrywający złożoność całego systemu.
* **Composite:** Rekurencyjne drzewa danych (Portfele inwestycyjne) z zachowaniem zasady Liskov.
* **Singleton (Spring Scope):** Świadome zarządzanie stanem aplikacji w środowisku wielowątkowym.

Taki zestaw wzorców ułatwia rozwój projektu, utrzymanie kodu i szybkie zrozumienie architektury przez nowych członków zespołu.

## 🧪 Testy integracyjne (`tc` i `local-pg`)
Projekt rozdziela środowisko CI/CD i debugowanie lokalne za pomocą dwóch profili Spring:
- **`tc`** — domyślny, powtarzalny profil testowy uruchamiający PostgreSQL automatycznie przez Docker CLI,
- **`local-pg`** — profil do debugowania i inspekcji danych na lokalnym PostgreSQL (`localhost:5432`).

### Szybki start testów po poprawce (`local-pg` i `tc`)

Po ostatniej poprawce testy integracyjne czyszczą stan bezpieczniej: w `local-pg` warto wykonać jawny cleanup testowej bazy, a w `tc` zwykle nie jest to potrzebne.

#### `local-pg` — lokalny PostgreSQL do debugowania i podglądu danych

```powershell
docker compose up -d db
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\clean-db.ps1 -Mode local-pg -Force
.\gradlew.bat "-Dlocal.pg=true" "-Denable.flyway=true" clean test --no-daemon
.\gradlew.bat "-Dlocal.pg=true" "-Denable.flyway=true" "-Dlocal.pg.keepdata=true" test --tests "pl.edu.praktyki.repository.CategorySpec" --no-daemon
```

#### `tc` — profil domyślny do powtarzalnych testów

```powershell
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" clean test --no-daemon
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.repository.CategorySpec" --no-daemon
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\clean-db.ps1 -Mode tc -Force
```

Pozostałe warianty uruchomienia, pojedyncze testy i cleanup są opisane w `scripts/Readme--Uruchamianie-aplikacji-i-testów.md`.

## Testy — dobra praktyka dotycząca asercji JSON

- Unikaj polegania na konkretnym porządku elementów w tablicach JSON (np. `content[0]`). API często nie gwarantuje stabilnego sortowania i takie asercje prowadzą do flakiness.
- Lepiej wyszukiwać elementy po unikalnych polach (np. `id` lub `originalId`) używając filtrów JSONPath: `$.content[?(@.id=='T1')]`.
- W Groovym, gdy używasz JSONPath w stringu double-quoted, pamiętaj o ucieczce znaku `$` (np. `"\$.content[?(@.id=='T1')]"`) aby uniknąć interpolacji GString.
- Alternatywnie możesz użyć `$.content[*].category` razem z Hamcrest `hasItem(...)` jeśli wystarczy sprawdzić tylko obecność wartości.




## ⚡ Asynchroniczność i zdarzenia w Springu

W projekcie wykorzystywane są `@Async` oraz zdarzenia aplikacyjne Springa, aby cięższe operacje — np. wysyłka raportów, audyt lub aktualizacja projekcji — mogły odbywać się w tle, bez blokowania głównego przepływu.

### `@Async` — podstawy

Adnotacja `@Async` działa przez proxy Springa. Oznacza to, że:
- metoda jest wykonywana w osobnym wątku,
- wywołanie wraca od razu do wywołującego,
- mechanizm działa poprawnie wtedy, gdy metoda jest wywoływana przez Springa, a nie bezpośrednio z tej samej klasy.

W projekcie używana jest własna pula wątków `bulkTaskExecutor`, co pozwala kontrolować liczbę workerów, długość kolejki i nazwy wątków widoczne w logach.

Przykład:

```groovy
@Async("bulkTaskExecutor")
void myHeavyMethod() {
    // ciężka praca wykonywana w tle
}
```

Warunki działania:
- w konfiguracji musi być włączone `@EnableAsync`,
- klasa musi być beanem Springa, np. `@Service`,
- nazwa executora w `@Async("...")` musi odpowiadać rzeczywiście zdefiniowanemu beanowi.

### Połączenie `@EventListener` + `@Async`

Połączenie `@EventListener` oraz `@Async` pozwala budować taski uboczne wykonywane po publikacji zdarzenia. W takim układzie metoda:
- nasłuchuje konkretnego zdarzenia opublikowanego przez `ApplicationEventPublisher`,
- uruchamia się automatycznie po publikacji zdarzenia,
- wykonuje swoją logikę asynchronicznie, w tle.

To pozwala rozdzielić główny przepływ biznesowy od zadań pobocznych, takich jak notyfikacje, logowanie techniczne, audyt czy aktualizacja read modelu.

Przykład:

```groovy
import groovy.util.logging.Slf4j
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Slf4j
class AsyncNotificationService {

    private final AtomicInteger processedEventsCount = new AtomicInteger(0)

    int getProcessedCount() {
        return processedEventsCount.get()
    }

    @Async("bulkTaskExecutor")
    @org.springframework.context.event.EventListener
    void handleBatchEvent(TransactionBatchProcessedEvent event) {
        log.info(">>> [ASYNCHRONICZNY-EVENT] Start przetwarzania raportu dla: {}", event.userName)
        sleep(2000)
        log.info(">>> [ASYNCHRONICZNY-EVENT] Raport dla bilansu {} PLN został przetworzony w tle.", event.totalBalance)
        processedEventsCount.incrementAndGet()
    }

    void reset() {
        processedEventsCount.set(0)
    }
}
```

### Jak to działa w praktyce

1. Główna logika publikuje zdarzenie, np. `TransactionBatchProcessedEvent`.
2. Spring wyszukuje wszystkie metody oznaczone `@EventListener`, które obsługują ten typ zdarzenia.
3. Ponieważ listener ma również `@Async("bulkTaskExecutor")`, jego wykonanie trafia do puli wątków zamiast blokować główny request lub batch.
4. Kod publikujący zdarzenie może zakończyć pracę szybciej, a zadanie poboczne wykonuje się niezależnie.

### Dlaczego w przykładzie używany jest `AtomicInteger`

Licznik `processedEventsCount` jest aktualizowany z wątków asynchronicznych, więc musi być bezpieczny współbieżnie. `AtomicInteger` gwarantuje poprawne inkrementowanie nawet wtedy, gdy kilka zdarzeń jest obsługiwanych równolegle. Metoda `getProcessedCount()` jest wygodnym punktem odczytu używanym np. w testach integracyjnych.

### Najważniejsze wnioski

- `@EventListener` odpowiada za nasłuchiwanie zdarzeń w aplikacji,
- `@Async` przenosi obsługę zdarzenia do wykonania w tle,
- połączenie obu mechanizmów dobrze nadaje się do zadań ubocznych, które nie powinny wydłużać głównego przepływu biznesowego.






