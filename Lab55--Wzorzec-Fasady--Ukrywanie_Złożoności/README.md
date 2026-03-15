Lab 55
------

Lab55: Wzorzec Fasady (Facade) - Ukrywanie Złożoności
-----------------------------------------------------

`Wzorzec Fasady (Facade)` to sposób na uproszczenie interfejsu do złożonego systemu. 
Fasada ukrywa złożoność systemu, oferując klientom prostszy interfejs do korzystania z niego.

Ten wzorzec to absolutny fundament czystej architektury. 
Właściwie, jeśli spojrzysz na nasz plik `SmartFinDbApp.groovy` (tam gdzie jest CLI), zauważysz coś bardzo niepokojącego dla Seniora: 
nasza klasa uruchomieniowa (CLI) jest "gruba". 
Sama wywołuje `CurrencyService`, potem `Ingester`, potem bawi się `Repository`, a na końcu składa raport z `AnalyticsService`.

Co jeśli jutro zrobimy aplikację mobilną i wystawimy to przez `REST API`? 
Będziemy musieli skopiować te 40 linijek kodu do Kontrolera! 
To łamie zasadę DRY (Don't Repeat Yourself).

Rozwiązaniem jest Fasada.
-------------------------

Cel: 
Stworzenie jednego, czystego punktu wejścia `Entrypoint` do naszego skomplikowanego systemu. 
`Fasada` weźmie na siebie całą "orkiestrację" (kierowanie ruchem), a klient (CLI lub REST) wywoła tylko jedną metodę.

Krok 1: Tworzenie Fasady (SmartFinFacade.groovy)
------------------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/facade/SmartFinFacade.groovy`:

```groovy
package pl.edu.praktyki.facade

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.service.*
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.domain.Transaction
import groovy.util.logging.Slf4j

@Service
@Slf4j
class SmartFinFacade {

    // Fasada ukrywa w sobie całą złożoność podsystemu (wstrzykuje 5 różnych klas!)
    @Autowired TransactionIngesterService ingester
    @Autowired CurrencyService currencySvc
    @Autowired FinancialAnalyticsService analyticsSvc
    @Autowired ReportGeneratorService reportSvc
    @Autowired TransactionRepository repo

    /**
     * To jest JEDYNA metoda, o której musi wiedzieć świat zewnętrzny (CLI, REST, GUI).
     */
    String processAndGenerateReport(String userName, List<Transaction> rawTransactions, List<String> rules) {
        log.info(">>> [FASADA] Rozpoczynam kompleksowe przetwarzanie dla użytkownika: {}", userName)

        // 1. Przeliczanie walut
        rawTransactions.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency ?: "PLN")
            tx.amountPLN = tx.amount * rate
        }

        // 2. Reguły i Import
        ingester.ingestAndApplyRules([rawTransactions], rules)

        // 3. Zapis do bazy (Mapowanie)
        def entities = rawTransactions.collect { tx ->
            new TransactionEntity(
                originalId: tx.id, date: tx.date, amount: tx.amount, currency: tx.currency,
                amountPLN: tx.amountPLN, category: tx.category, description: tx.description, tags: tx.tags
            )
        }
        repo.saveAll(entities)

        // 4. Odczyt historii
        def allHistory = repo.findAll().collect { ent ->
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
        log.info(">>> [FASADA] Przetwarzanie zakończone. Generuję raport.")
        return reportSvc.generateMonthlyReport(userName, stats)
    }
}
```

Krok 2: Odchudzenie Klienta (Refaktoryzacja SmartFinDbApp.groovy)
-----------------------------------------------------------------

Teraz zobacz, jak magicznie "chudnie" nasz interfejs użytkownika!

Wejdź do `SmartFinDbApp.groovy` i zamień całą logikę w klasie `SmartFinCliRunner` na użycie `Fasady`.

```groovy
// ... importy ...
import pl.edu.praktyki.facade.SmartFinFacade // <-- DODAJ IMPORT

@Component
@Profile("!test")
class SmartFinCliRunner implements CommandLineRunner {

    // Zamiast 5 serwisów, wstrzykujemy tylko JEDNĄ Fasadę!
    @Autowired SmartFinFacade smartFinFacade 
    @Autowired CurrencyService currencySvc // Zostawiamy tylko do walidacji początkowej waluty

    @Override
    void run(String... args) {
        // ... (kod CLI: CliBuilder, parsowanie args, walidacja waluty - to zostaje bez zmian) ...
        // ... (tworzenie listy rawData zostaje bez zmian) ...

        def rules = ["if (amountPLN < -100) addTag('BIG_SPENDER')"]

        // =========================================================
        // TUTAJ DZIEJE SIĘ MAGIA FASADY
        // Wywalamy 30 linijek kodu i zastępujemy jedną metodą!
        // =========================================================
        String report = smartFinFacade.processAndGenerateReport(opts.u, rawData, rules)

        println "\n" + report
        def fileName = "db_report_${opts.u.replace(' ', '_')}.txt"
        new File(fileName).text = report
        println ">>> Raport zapisany: $fileName"
        println "=========================================\n"
    }
}
```

Krok 3: Test Spock (FacadeSpec.groovy)
--------------------------------------

Aby udowodnić, że Fasada działa jako pojedynczy punkt dostępu, napiszemy test, 
w którym Klient (Test) rozmawia tylko z nią.

Stwórz `src/test/groovy/pl/edu/praktyki/facade/FacadeSpec.groovy`:

```groovy
package pl.edu.praktyki.facade

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

@SpringBootTest
class FacadeSpec extends Specification {

    // Wstrzykujemy TYLKO Fasadę - nie interesują nas poszczególne serwisy
    @Autowired
    SmartFinFacade facade

    def "powinien przetworzyć cały proces biznesowy przez jeden punkt dostępu (Fasada)"() {
        given: "dane wejściowe od użytkownika (np. z CLI lub z REST Controller)"
        def userName = "Testowy Użytkownik"
        def data =[
            new Transaction(id: "F1", amount: 100.0, currency: "PLN", category: "Test", date: LocalDate.now())
        ]
        def rules =["if (amountPLN < -100) addTag('BIG_SPENDER')"]

        when: "wywołujemy GŁÓWNĄ metodę Fasady"
        // Klient (nasz test) nie wie o istnieniu repozytoriów, walut ani reguł.
        // Wywołuje tylko jedną metodę, a Fasada orkiestruje resztę.
        String generatedReport = facade.processAndGenerateReport(userName, data, rules)

        then: "cały proces zakończył się sukcesem, zwracając gotowy raport"
        generatedReport != null
        generatedReport.contains("RAPORT FINANSOWY DLA: TESTOWY UŻYTKOWNIK")
        
        and: "raport zawiera przetworzone dane (np. 100 PLN na plusie)"
        generatedReport.contains("Status: NA PLUSIE")
        
        // Opcjonalnie: Wydrukuj raport na konsolę, aby zobaczyć efekt pracy Fasady
        println ">>> RAPORT Z FASADY:\n$generatedReport"
    }
}
```


Dlaczego to jest Wzorzec "Facade" na poziomie Seniora? (Do rozmowy rekrutacyjnej)

Kiedy rekruter zapyta Cię: "Kiedy stosujesz wzorzec Fasady?", Twoja odpowiedź brzmi:

Stosuję Fasadę, gdy warstwa prezentacji (CLI, REST Controller, UI) staje się zbyt 'gruba' i zaczyna wiedzieć za dużo o architekturze systemu.
Zamiast pozwalać Kontrolerowi REST na wstrzykiwanie 6 różnych serwisów (do walut, reguł, zapisu do bazy i raportowania), tworzę Fasadę. 
Kontroler wywołuje tylko jedną metodę Fasady `processAndGenerateReport`.
Dzięki temu, jeśli jutro zrezygnujemy z bazy `H2` na rzecz `MongoDB`, albo zmienimy silnik reguł z `GroovyShell` na `Drools`, 
muszę zmienić kod tylko wewnątrz Fasady. 
Kod moich Kontrolerów `REST` i skryptów `CLI` pozostaje nietknięty, bo one w ogóle nie wiedzą o istnieniu tych serwisów. 
To drastycznie zmniejsza sprzężenie (Coupling) w systemie.



Podsumowanie:
-------------
- Fasada to pojedynczy punkt dostępu do złożonego systemu.
- Umożliwia odseparowanie warstwy prezentacji od logiki biznesowej i danych.
- Ułatwia utrzymanie i rozwój systemu, ponieważ zmiany w logice biznesowej nie wpływają na warstwę prezentacji.
- Jest kluczowym elementem czystej architektury, pomagającym utrzymać kod "czysty" i łatwy do zrozumienia.
- Testy pokazują, że klient (test) rozmawia tylko z Fasadą, a nie z poszczególnymi serwisami, co potwierdza, że Fasada jest jedynym punktem dostępu do systemu.
- Fasada jest idealna do sytuacji, gdy mamy wiele serwisów lub komponentów, które muszą współpracować, ale nie chcemy, aby klienci (np. Kontrolery REST) musieli znać szczegóły tej współpracy.
- Dzięki Fasady, możemy łatwo zmienić implementację wewnętrzną (np. zmienić bazę danych, silnik reguł, sposób generowania raportów) bez wpływu na kod klientów, co jest kluczowe dla utrzymania niskiego sprzężenia i wysokiej spójności w systemie.
- Fasada jest również świetnym miejscem do dodania logowania, obsługi wyjątków i innych aspektów przekrojowych, które dotyczą całego procesu biznesowego, bez zaśmiecania kodu klientów.
- Wzorzec Fasady jest często stosowany w architekturze warstwowej, gdzie warstwa prezentacji (UI, REST) korzysta z warstwy usług (Service Layer) poprzez Fasadę, która ukrywa złożoność logiki biznesowej i dostępu do danych.
- Fasada jest również przydatna w sytuacjach, gdy chcemy zapewnić spójny interfejs do różnych systemów zewnętrznych lub wewnętrznych, które mogą mieć różne API lub protokoły komunikacyjne.
- Fasada jest jednym z fundamentalnych wzorców projektowych, który pomaga w utrzymaniu czystej architektury i jest często używany w połączeniu z innymi wzorcami, takimi jak `Singleton`, `Factory`, `Strategy` czy `Observer`, aby stworzyć elastyczny i łatwy do utrzymania system.
- Warto pamiętać, że Fasada nie powinna być zbyt "gruba" - jej zadaniem jest uproszczenie interfejsu, a nie dodawanie dodatkowej logiki biznesowej.
- Fasada powinna być łatwa do zrozumienia i użycia, a jej metoda (np. `processAndGenerateReport`) powinna być intuicyjna i dobrze nazwana, aby jasno komunikować, co robi.
- Fasada jest również świetnym miejscem do implementacji wzorca `Template Method`, gdzie możemy zdefiniować szkielet algorytmu (np. proces przetwarzania danych i generowania raportu), a poszczególne kroki mogą być delegowane do innych serwisów, co pozwala na łatwe rozszerzanie i modyfikowanie procesu bez zmiany interfejsu Fasady.
- Fasada jest kluczowym elementem w architekturze mikroserwisów, gdzie może służyć jako punkt integracji dla różnych mikroserwisów, zapewniając spójny interfejs dla klientów, jednocześnie ukrywając złożoność komunikacji między mikroserwisami.
- Fasada jest również często używana w testach, gdzie może służyć jako "mock" lub "stub" dla złożonych systemów, umożliwiając testowanie klientów bez konieczności uruchamiania całego systemu, co przyspiesza testy i ułatwia izolację problemów.
- Fasada jest jednym z kluczowych wzorców, które pomagają w utrzymaniu czystej architektury, a jej stosowanie jest oznaką dojrzałości i profesjonalizmu w projektowaniu systemów.


Moje pytanie - takie naiwne może:
---------------------------------

tylko to glupie , bo de facto w klasie fasady SmartFinFacade.groovy mamy wywolania tych wszystkich servisów. Przenosimy tylko z CLI 

Odpowiedź:
----------

Masz 100% racji! Zauważyłeś rzecz, która na pierwszy rzut oka wydaje się całkowicie bez sensu. 
Mechanicznie patrząc – wzięliśmy 40 linijek kodu z pliku A i po prostu wkleiliśmy je do pliku B.

Skoro to ten sam kod, to po co tworzyć nową klasę i nazywać to dumnie "Wzorcem Projektowym"?
Odpowiedź kryje się w słowie: Skalowalność i Punkty Wejścia (Entrypoints).
Wyobraź sobie, że Twój system odnosi sukces. Szef przychodzi i mówi:
"Chcę, żeby to narzędzie nadal działało w konsoli (CLI) dla administratorów."
"Ale chcę też, żeby nasza aplikacja mobilna mogła to wywoływać przez REST API."
"Aha, i zróbmy jeszcze, żeby to się generowało automatycznie co noc o 24:00 przez Scheduler."
Scenariusz BEZ Fasady (Koszmar)
Gdybyś zostawił ten kod w SmartFinCliRunner, musiałbyś skopiować te same 40 linijek (wywoływanie Ingestera, walut, reguł, bazy i raportu) w trzy miejsca:
1. Do SmartFinCliRunner (dla konsoli).
2. Do TransactionController (dla REST API).
3. Do NightlyBatchJob (dla harmonogramu).

Jeśli jutro dodasz nowy serwis (np. EmailSenderService), musisz pamiętać, żeby dopisać go w trzech różnych plikach. 
Zapomnisz o jednym i na produkcji wybucha pożar.

Scenariusz Z Fasadą (Architektura Seniora)
------------------------------------------

Dzięki temu, że przenieśliśmy te 40 linijek do SmartFinFacade, Twoja architektura wygląda teraz tak:

```groovy
// 1. Zwykły skrypt w konsoli
class SmartFinCliRunner {
@Autowired SmartFinFacade facade
void run() { facade.processAndGenerateReport(...) }
}

// 2. Kontroler REST dla aplikacji mobilnej
class TransactionController {
@Autowired SmartFinFacade facade
@PostMapping("/generate")
String generate() { return facade.processAndGenerateReport(...) }
}

// 3. Nocne zadanie w tle
class NightlyJob {
@Autowired SmartFinFacade facade
@Scheduled(cron = "0 0 0 * * ?")
void doNightly() { facade.processAndGenerateReport(...) }
}
```

Metafora z Restauracji
----------------------

Serwisy (Currency, Analytics, Ingester): 
To kucharze. Jeden kroi, drugi smaży, trzeci gotuje makaron.

CLI Runner / Rest Controller: 
To kelner. 
Jego jedynym zadaniem jest odebrać zamówienie od klienta (-u Jacek) i podać mu gotowy talerz (Raport). 
Kelner nie powinien wchodzić do kuchni i dyrygować kucharzami!

Fasada: 
To Szef Kuchni (Head Chef). 
Kelner mówi Szefowi Kuchni: 
"Stolik nr 5 chce raport".
Szef Kuchni (Fasada) odwraca się do kucharzy (Serwisów) i koordynuje ich pracę.

Podsumowanie
Przeniesienie kodu z CLI do Fasady to oddzielenie Warstwy Prezentacji (Interfejsu Użytkownika) 
od Warstwy Aplikacji (Logiki sterującej). 
To jest absolutny fundament tzw. Clean Architecture (Czystej Architektury) lub Hexagonal Architecture.

