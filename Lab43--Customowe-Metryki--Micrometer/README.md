Lab 43
------

To jest absolutny "must-have" w systemach produkcyjnych. 
Dzięki metrykom biznesowym (zamiast tylko technicznych typu "ile RAMu używa appka"), biznes widzi, jak działa system w czasie rzeczywistym.

Lab 32: Customowe Metryki (Micrometer)
--------------------------------------

Cel: 
Stworzymy dwa mierniki:
1. Counter (licznik): Ile transakcji przetworzyliśmy w sumie.
2. Gauge (wskaźnik): Aktualna średnia wartość transakcji z bazy danych.

Krok 32.1: Zależność (build.gradle)
-----------------------------------

Spring Boot 3 używa biblioteki Micrometer. 
Powinieneś mieć już spring-boot-starter-actuator w build.gradle z poprzednich kroków. Jeśli nie, dodaj:

implementation 'org.springframework.boot:spring-boot-starter-actuator'

Krok 32.2: Serwis Metryk (FinanceMetrics.groovy)
------------------------------------------------

Stwórz klasę, która będzie zarządzać metrykami. 
Użyjemy `MeterRegistry`, który jest głównym interfejsem `Micrometera`.

Stwórz `src/main/groovy/pl/edu/praktyki/monitoring/FinanceMetrics.groovy`:

```groovy
package pl.edu.praktyki.monitoring

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class FinanceMetrics {

    // 1. Licznik transakcji (Counter)
    private final io.micrometer.core.instrument.Counter transactionCounter

    // 2. Średnia wartość (Gauge)
    private final AtomicReference<Double> averageValue = new AtomicReference<>(0.0)

    FinanceMetrics(MeterRegistry registry) {
        // Rejestracja licznika
        this.transactionCounter = registry.counter("smartfin.transactions.processed")

        // Rejestracja Gauge'a (wskaźnika) - Spring będzie pytał ten obiekt o wartość
        registry.gauge("smartfin.transactions.avg.value", averageValue)
    }

    void recordTransaction(BigDecimal amount) {
        transactionCounter.increment()
    }

    void updateAverage(BigDecimal avg) {
        averageValue.set(avg.doubleValue())
    }
}
```

Krok 32.3: Integracja w TransactionIngesterService
--------------------------------------------------

Teraz musimy "podpiąć" te metryki tam, gdzie dzieje się akcja.
Edytuj `src/main/groovy/pl/edu/praktyki/service/TransactionIngesterService.groovy`:

```groovy
// ... importy ...
@Autowired FinanceMetrics metrics // Wstrzykujemy nasz serwis metryk

    // Wewnątrz metody ingestAndApplyRules w pętli .each { tx -> ... } dodaj:
    source.each { tx ->
        ruleService.applyRules(tx, rules)
        eventPublisher.publishEvent(new TransactionImportedEvent(transaction: tx))
        
        // Zliczamy!
        metrics.recordTransaction(tx.amountPLN)
    }
```

Krok 32.4: Jak to przetestować / zobaczyć?
------------------------------------------

Uruchom aplikację: ./gradlew runSmartFinDb -PappArgs="-u Jacek"
Otwórz przeglądarkę i wpisz:
👉 http://localhost:8080/actuator/metrics/smartfin.transactions.processed
Zobaczysz JSON-a:
 
JSON
{
    "name": "smartfin.transactions.processed",
    "measurements": [
        {
            "statistic": "COUNT",
            "value": 3
        }
    ],
    "availableTags": []
}

Wyzwanie Finałowe dla Lab 32:
-----------------------------

Aby aplikacja była w pełni "Mid-level", dodajmy automatyczną aktualizację średniej ceny w bazie danych.

Zadanie: 
W `DailyReportScheduler.groovy` (który stworzyliśmy w Labie 36), dodaj logikę, która po każdym obliczeniu raportu wywoła:
              `financeMetrics.updateAverage(averageValue)`.

Test: 
Napisz test Spocka dla FinanceMetrics, w którym sprawdzisz, czy po wywołaniu recordTransaction licznik (przez meter.count()) faktycznie wzrasta.
Wskazówka do testu: Aby sprawdzić wartość licznika w testach, użyj:

```groovy
def registry = new SimpleMeterRegistry()
def metrics = new FinanceMetrics(registry)
metrics.recordTransaction(100.0)
assert registry.get("smartfin.transactions.processed").counter().count() == 1.0
```

Rozwiązanie
-----------

Rozwiązanie tego zadania pozwoli Ci zrozumieć, jak działają metryki w Spring Boot i jak można je wykorzystać do monitorowania aplikacji 
w czasie rzeczywistym.

Zaimplementujmy to tak, aby `FinanceMetrics` automatycznie pilnował bilansu, a `OrderService` (lub Twoja klasa importująca) 
informował go o każdej zmianie.

Oto kompletna implementacja wyzwania.

1. Zaktualizowana klasa FinanceMetrics.groovy
---------------------------------------------

Musimy dodać drugi Gauge, który będzie przechowywał AtomicReference<BigDecimal>.

```groovy
package pl.edu.praktyki.monitoring

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class FinanceMetrics {

    private final io.micrometer.core.instrument.Counter transactionCounter
    // Używamy AtomicReference dla bezpieczeństwa wątkowego z BigDecimal
    private final AtomicReference<BigDecimal> totalBalanceGauge = new AtomicReference<>(BigDecimal.ZERO)

    FinanceMetrics(MeterRegistry registry) {
        this.transactionCounter = registry.counter("smartfin.transactions.processed")
        
        // Gauge dla bilansu (smartfin.transactions.total.balance)
        // Spring automatycznie wyciągnie wartość z AtomicReference
        registry.gauge("smartfin.transactions.total.balance", totalBalanceGauge, { ref -> ref.get().doubleValue() })
    }

    void recordTransaction(BigDecimal amount) {
        transactionCounter.increment()
    }

    void updateBalance(BigDecimal newBalance) {
        totalBalanceGauge.set(newBalance)
    }
}
```

2. Wykorzystanie w SmartFinDbApp.groovy
---------------------------------------

W Twoim głównym procesie (tam gdzie importujesz dane), po zapisie do bazy, wyślij sygnał do metryk:

```groovy
// ... wewnątrz SmartFinDbApp.run ...
repo.saveAll(entitiesToSave)
```


// Oblicz bilans z BAZY i zaktualizuj metrykę
def allHistory = repo.findAll()
def currentBalance = analyticsSvc.calculateTotalBalance(allHistory.collect { ... })
financeMetrics.updateBalance(currentBalance)


3. Testowanie metryk w Spocku (FinanceMetricsSpec.groovy)
---------------------------------------------------------

To jest najważniejsza część – test, który udowadnia, że Twój system monitoringu działa poprawnie. 
Używamy `SimpleMeterRegistry` – to specjalna implementacja Micrometera "w pamięci", 
która nie potrzebuje całego Springa do działania.

Stwórz `src/test/groovy/pl/edu/praktyki/monitoring/FinanceMetricsSpec.groovy`:

```groovy
package pl.edu.praktyki.monitoring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

class FinanceMetricsSpec extends Specification {

    def "powinien poprawnie aktualizować wskaźnik bilansu (Gauge)"() {
        given: "czysty rejestr metryk"
        def registry = new SimpleMeterRegistry()
        def metrics = new FinanceMetrics(registry)

        when: "aktualizujemy bilans"
        metrics.updateBalance(1234.56)

        then: "wskaźnik w rejestrze zwraca wartość 1234.56"
        // SimpleMeterRegistry pozwala nam wyciągnąć gauge i sprawdzić jego wartość
        registry.get("smartfin.transactions.total.balance").gauge().value() == 1234.56
    }

    def "powinien inkrementować licznik transakcji"() {
        given:
        def registry = new SimpleMeterRegistry()
        def metrics = new FinanceMetrics(registry)

        when:
        metrics.recordTransaction(100.0)
        metrics.recordTransaction(50.0)

        then:
        registry.get("smartfin.transactions.processed").counter().count() == 2.0
    }
}
```

Dlaczego to jest sukces?

Separacja: 
FinanceMetrics nie wie o bazie danych ani o Springu – wie tylko o MeterRegistry. 
To czyni go bardzo łatwym do przetestowania.

Observability: 
Każdy system w chmurze (np. na AWS czy Azure) w 2026 roku wymaga metryk typu /actuator/metrics. 
Ty masz je teraz "za darmo" i z pełną kontrolą nad tym, co dokładnie mierzysz.

Weryfikowalność: 
Twój test FinanceMetricsSpec daje Ci gwarancję, że jeśli zmieni się biblioteka Micrometer, 
dowiesz się o tym w milisekundę podczas budowania projektu.




info o co tu chodzi:
--------------------

Masz pełne prawo tak czuć! Zatrzymajmy się.

Rzuciliśmy się tu na głęboką wodę architektury oprogramowania. Zderzyły się tu naraz 4 różne światy: Baza Danych, Logika Biznesowa, Translacja Danych (Mapowanie) i Monitoring. To jest mega skomplikowane dla każdego, kto robi to pierwszy raz.

Zapomnijmy na chwilę o kodzie. Wyobraź sobie tę sytuację w prawdziwym świecie.

Metafora: Księgowy i Tablica Elektroniczna

Wyobraź sobie, że prowadzisz firmę:

Sejf w biurze = Twoja Baza Danych (repo).

Twój Księgowy = Serwis Analityczny (analyticsSvc).

Tablica LED na ścianie = Monitoring (financeMetrics).

Kiedy przychodzi nowy dzień (uruchamiasz aplikację i ładujesz nowe transakcje), wydarza się taka historia:

KROK 1: repo.saveAll(...)
Otwierasz sejf i dorzucasz do niego nowe dokumenty, które dzisiaj przyszły.

KROK 2: repo.findAll()
Chcesz wiedzieć, ile dokładnie firma ma teraz pieniędzy. Wyciągasz z sejfu absolutnie wszystkie dokumenty od początku istnienia firmy.

KROK 3: dbRecords.collect { ... } (To to trudne mapowanie)
Dokumenty wyciągnięte z sejfu są włożone w grube, pancerne koszulki ochronne (to są obiekty TransactionEntity – stworzone specjalnie dla bazy danych).
Idziesz do Księgowego, a on krzyczy: "Nie umiem czytać przez ten plastik! Przepisz mi to na zwykłe, czyste kartki papieru!".
Więc wyciągasz każdy dokument z koszulki i przepisujesz go na zwykłą kartkę (to są czyste obiekty Transaction). To jest ten blok .collect { new Transaction(...) }.

KROK 4: analyticsSvc.calculateTotalBalance(...)
Dajesz czyste kartki Księgowemu. On uderza w kalkulator i mówi: "Nasz całkowity, historyczny bilans to dokładnie 12 500 PLN".

KROK 5: financeMetrics.updateBalance(currentBalance)
Bierzesz te 12 500 PLN, podchodzisz do Tablicy LED na ścianie i wpisujesz tam ten wynik.

Dlaczego ta Tablica (Metrics) jest w ogóle potrzebna?

Po co to wszystko?
Wyobraź sobie, że przychodzi Szef (np. zewnętrzny program sprawdzający zdrowie aplikacji przez /actuator/metrics). Jeśli zapyta o bilans firmy, Ty nie idziesz znowu do Księgowego, Księgowy nie idzie znowu do Sejfu, nie ściągacie znowu plastików i nie liczycie wszystkiego od nowa. To by trwało wieki!

Szef po prostu patrzy na Tablicę LED na ścianie i natychmiast widzi wynik. A tablica aktualizuje się tylko wtedy, gdy dodasz nowe dokumenty.

Spójrz teraz na ten kod jeszcze raz przez pryzmat tej historii:
code
Groovy
download
content_copy
expand_less
// 1. Wrzucamy dzisiejsze dokumenty do Sejfu
repo.saveAll(entitiesToSave)

// 2. Wyciągamy wszystko z Sejfu (w koszulkach)
def dbRecords = repo.findAll()

// 3. Przepisujemy z koszulek (Entity) na zwykłe kartki (Transaction)
def allHistory = dbRecords.collect { ent ->
new Transaction(
id: ent.originalId,
amount: ent.amount,
amountPLN: ent.amountPLN,
// ... reszta przepisanych danych
)
}

// 4. Księgowy liczy całkowity bilans z czystych kartek
def currentBalance = analyticsSvc.calculateTotalBalance(allHistory)

// 5. Wpisujemy wynik na Tablicę LED na ścianie
financeMetrics.updateBalance(currentBalance)

To wszystko.
Nic więcej się tam nie ukrywa. W Springu często najwięcej kodu zajmuje po prostu "przepisywanie" danych z formatu bazy danych na format biznesowy.

Czy ta historia chociaż trochę pomogła poukładać sobie w głowie, "kto tu z kim rozmawia i po co"? Jeśli to nadal sprawia ból głowy – możemy ten klocek pominąć. Projekt i bez tego jest wybitny. Daj znać, jak to czujesz!









