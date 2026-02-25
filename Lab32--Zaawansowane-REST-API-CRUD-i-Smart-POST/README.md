Lab 32
------

Tutaj Groovy błyszczy szczególnie jasno w testach, ponieważ w Javie budowanie JSON-a do żądania POST wymaga 
tworzenia zagnieżdżonych stringów (co wygląda okropnie) albo używania ObjectMapper z Jacksona. 
W Groovy zrobimy to używając wbudowanych map.

Przed Tobą Lab 32: Zaawansowane REST API (CRUD) i Smart POST.
-------------------------------------------------------------

Cel:
Stworzymy endpoint POST /api/transactions, który przyjmie nową transakcję, od razu przeliczy jej walutę i nada tagi, a następnie zapisze do bazy.
Stworzymy endpoint GET /api/transactions/{id}, który w razie braku transakcji poprawnie zwróci błąd HTTP 404 (Not Found).
Przetestujemy to w Spocku.


Pójście w stronę rozbudowy REST API to strzał w dziesiątkę! W prawdziwych projektach aplikacja rzadko kiedy ma tylko GET. Musi przyjmować dane z zewnątrz (np. z aplikacji mobilnej czy frontendu w Reakcie) i odpowiednio na nie reagować.
Tutaj Groovy błyszczy szczególnie jasno w testach, ponieważ w Javie budowanie JSON-a do żądania POST wymaga tworzenia zagnieżdżonych stringów (co wygląda okropnie) albo używania ObjectMapper z Jacksona. W Groovy zrobimy to używając wbudowanych map.
Przed Tobą Lab 32: Zaawansowane REST API (CRUD) i Smart POST.
Cel:
Stworzymy endpoint POST /api/transactions, który przyjmie nową transakcję, od razu przeliczy jej walutę i nada tagi, a następnie zapisze do bazy.
Stworzymy endpoint GET /api/transactions/{id}, który w razie braku transakcji poprawnie zwróci błąd HTTP 404 (Not Found).
Przetestujemy to w Spocku.


Krok 1: Rozbudowa Kontrolera (TransactionController.groovy)
-----------------------------------------------------------

Dodajmy do Twojego kontrolera dwa nowe endpointy. 
Zwróć uwagę, jak w metodzie addTransaction wykorzystujemy nasze istniejące serwisy (waluty i reguły), 
aby nowe dane dodawane przez API były tak samo "mądre" jak te z plików.

Otwórz `TransactionController.groovy` i dodaj potrzebne wstrzyknięcia oraz metody:

```groovy
package pl.edu.praktyki.web

import org.springframework.web.bind.annotation.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.service.FinancialAnalyticsService
import pl.edu.praktyki.service.CurrencyService
import pl.edu.praktyki.service.TransactionRuleService
import pl.edu.praktyki.domain.Transaction

@RestController
@RequestMapping("/api/transactions")
class TransactionController {

    @Autowired TransactionRepository repo
    @Autowired FinancialAnalyticsService analyticsService
    @Autowired CurrencyService currencyService
    @Autowired TransactionRuleService ruleService

    // ... (Twoje poprzednie metody: getAll() i getStats() zostają tutaj) ...

    @GetMapping("/{dbId}")
    Transaction getById(@PathVariable Long dbId) {
        // Używamy orElseThrow do eleganckiej obsługi braku rekordu
        def entity = repo.findById(dbId).orElseThrow {
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Transakcja o ID $dbId nie istnieje")
        }
        
        return new Transaction(
            id: entity.originalId,
            date: entity.date,
            amount: entity.amount,
            currency: entity.currency,
            amountPLN: entity.amountPLN,
            category: entity.category,
            description: entity.description,
            tags: entity.tags
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // Zwróci kod 201 zamiast 200
    Transaction addTransaction(@RequestBody Transaction dto) {
        // 1. Logika biznesowa: Przelicz walutę
        def rate = currencyService.getExchangeRate(dto.currency ?: "PLN")
        dto.amountPLN = dto.amount * rate

        // 2. Logika biznesowa: Aplikuj reguły (tu na sztywno, w prod mogłyby być z bazy)
        def rules =["if (amountPLN < -100) addTag('BIG_SPENDER')"]
        ruleService.applyRules(dto, rules)

        // 3. Mapowanie na Encję i Zapis
        def entity = new TransactionEntity(
            originalId: dto.id,
            date: dto.date ?: java.time.LocalDate.now(),
            amount: dto.amount,
            currency: dto.currency ?: "PLN",
            amountPLN: dto.amountPLN,
            category: dto.category,
            description: dto.description,
            tags: dto.tags
        )
        def savedEntity = repo.save(entity)

        // Zwracamy zaktualizowane DTO
        return dto
    }
}
```

Krok 2: Testy POST i 404 w Spocku (TransactionControllerSpec.groovy)
--------------------------------------------------------------------

Teraz wykorzystamy potęgę Groovy'ego do testowania API. 
Zamiast tworzyć Transaction, stworzymy po prostu mapę [amount: 100, currency: "USD"] i użyjemy JsonOutput.toJson(), 
co jest 10x szybsze w pisaniu.

Otwórz `TransactionControllerSpec.groovy` i dopisz te testy wewnątrz klasy:

```groovy
// Dodaj na górze pliku, jeśli nie masz:
    // import groovy.json.JsonOutput
    // import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

    def "GET /api/transactions/{id} powinien zwrócić 404 dla nieistniejącego ID"() {
        expect: "próba pobrania rekordu nr 9999 kończy się błędem 404"
        mvc.perform(get("/api/transactions/9999"))
           .andExpect(status().isNotFound())
    }

    def "POST /api/transactions powinien zapisać transakcję, przeliczyć walutę i nadać tagi"() {
        given: "nowa transakcja w formacie JSON zbudowana za pomocą mapy Groovy"
        // Zobacz jak czysto to wygląda! Żadnych klas, po prostu definicja danych.
        def newTxPayload =[
            id: "NEW-1",
            amount: -50,
            currency: "USD",
            category: "Gry",
            description: "Zakup na Steam"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(newTxPayload)

        when: "wysyłamy żądanie POST"
        def response = mvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))

        then: "status HTTP to 201 Created"
        response.andExpect(status().isCreated())

        and: "waluta została przeliczona i zwrócona w odpowiedzi"
        // amountPLN nie wysyłaliśmy! API samo to policzyło. Sprawdzamy czy istnieje (nie jest null)
        response.andExpect(jsonPath('$.amountPLN').exists())

        and: "transakcja otrzymała tag (ponieważ -50 USD to więcej niż -100 PLN)"
        response.andExpect(jsonPath('$.tags[0]').value("BIG_SPENDER"))

        and: "baza danych powiększyła się o nowy rekord"
        repository.findAll().size() == 3 // 2 z metody setup() + 1 nowy
    }
```

Dlaczego ta lekcja jest kluczowa dla Twojego portfolio?
Kiedy będziesz pokazywał kod na rozmowie technicznej, zwrócą uwagę na to, że:

Zabezpieczyłeś endpoint GET /{id}: 
Użycie .orElseThrow { new ResponseStatusException(...) } to nowoczesny standard w Spring Boot.

"Smart Endpoint": 
Twój POST to nie jest głupie przepychanie JSON-a do bazy. 
Przechwytuje dane, waliduje, przelicza walutę, używa GroovyShell do tagowania i dopiero zapisuje. 
To jest prawdziwa logika biznesowa.

Praktyczne testy: 
Użycie JsonOutput.toJson([id: "A", amount: 10]) w Spocku to ulubiony trik programistów. 
Pozwala uniknąć utrzymywania wielkich plików testowych JSON.


Musimy oddzielić "Konfigurację aplikacji" od "Skryptu CLI" i powiedzieć Springowi: "Hej, kiedy puszczam testy, NIE URUCHAMIAJ części konsolowej".
Użyjemy do tego Spring Profiles (@Profile).

Krok 1: Refaktoryzacja SmartFinDbApp.groovy
-------------------------------------------

Otwórz plik `src/main/groovy/pl/edu/praktyki/SmartFinDbApp.groovy`.
Rozdzielimy go na dwie klasy w tym samym pliku: jedną główną (`SmartFinDbApp`) 
i drugą odpowiadającą za logikę CLI (`SmartFinCliRunner`), która będzie wyłączona w trakcie testów.
Podmień zawartość na poniższą:

```groovy
package pl.edu.praktyki

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.CommandLineRunner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.cache.annotation.EnableCaching
import groovy.cli.picocli.CliBuilder
import pl.edu.praktyki.service.*
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// 1. GŁÓWNA KLASA (Teraz jest czysta, tylko startuje Springa)
@SpringBootApplication
@EnableCaching
class SmartFinDbApp {
    static void main(String[] args) {
        SpringApplication.run(SmartFinDbApp, args)
    }
}

// 2. KLASA URUCHOMIENIOWA CLI
@Component
@Profile("!test") // <-- MAGIA: Uruchomi się zawsze, CHYBA ŻE aktywny jest profil "test"
class SmartFinCliRunner implements CommandLineRunner {

    @Autowired TransactionIngesterService ingester
    @Autowired CurrencyService currencySvc
    @Autowired FinancialAnalyticsService analyticsSvc
    @Autowired ReportGeneratorService reportSvc
    @Autowired TransactionRepository repo

    @Override
    void run(String... args) {
        // ... CAŁY TWÓJ KOD Z POPRZEDNIEJ METODY run() ...
        
        def cli = new CliBuilder(usage: 'smart-fin-db -u <user> [-c <currency>]')
        cli.with {
            u longOpt: 'user', args: 1, required: true, 'Imię i nazwisko użytkownika'
            c longOpt: 'currency', args: 1, 'Waluta bazowa (domyślnie PLN)'
            h longOpt: 'help', 'Pokaż pomoc'
        }

        def opts = cli.parse(args)
        if (!opts || opts.h) return

        println "\n========================================="
        println ">>> Uruchamianie wersji z BAZĄ DANYCH (H2)..."
        
        def targetCurrency = opts.c ?: "PLN"
        if (targetCurrency != "PLN") {
            def rate = currencySvc.getExchangeRate(targetCurrency)
            if (rate == null) {
                System.err.println "BŁĄD: Waluta $targetCurrency nie jest obsługiwana."
                return 
            }
        }

        def rawData =[
            new Transaction(id: "1", amount: 100, currency: "EUR", category: "Jedzenie", description: "Obiad", date: LocalDate.now()),
            new Transaction(id: "2", amount: -50, currency: "USD", category: "Rozrywka", description: "Kino", date: LocalDate.now()),
            new Transaction(id: "3", amount: 2000, currency: "PLN", category: "Praca", description: "Bonus", date: LocalDate.now())
        ]

        rawData.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency)
            tx.amountPLN = tx.amount * rate
        }

        def rules = ["if (amountPLN < -100) addTag('BIG_SPENDER')"]
        ingester.ingestAndApplyRules([rawData], rules)

        def entitiesToSave = rawData.collect { tx ->
            new pl.edu.praktyki.repository.TransactionEntity(
                originalId: tx.id, date: tx.date, amount: tx.amount, currency: tx.currency,
                amountPLN: tx.amountPLN, category: tx.category, description: tx.description, tags: tx.tags
            )
        }
        repo.saveAll(entitiesToSave)

        def dbRecords = repo.findAll()
        println ">>> W bazie znajduje się obecnie ${dbRecords.size()} transakcji."

        def allHistory = dbRecords.collect { ent ->
            new Transaction(
                id: ent.originalId, date: ent.date, amount: ent.amount, currency: ent.currency,
                amountPLN: ent.amountPLN, category: ent.category, description: ent.description, tags: ent.tags
            )
        }

        def stats =[
            totalBalance: analyticsSvc.calculateTotalBalance(allHistory),
            topCategory: analyticsSvc.getTopSpendingCategory(allHistory),
            spendingMap: analyticsSvc.getSpendingByCategory(allHistory)
        ]

        String report = reportSvc.generateMonthlyReport(opts.u, stats)

        println "\n" + report
        def fileName = "db_report_${opts.u.replace(' ', '_')}.txt"
        new File(fileName).text = report
        println ">>> Raport zapisany: $fileName"
        println "=========================================\n"
    }
}
```


Zadanie:
Zaimplementuj te zmiany i uruchom `./gradlew test`


Wyzwanie dodatkowe (dla Ciebie):
Napisz test (w bloku def "..."), który próbuje wysłać zapytanie DELETE pod adres /api/transactions/1 i po prostu zwraca 200 OK (nawet jeśli nie dodałeś jeszcze metody w kontrolerze – napisz metodę @DeleteMapping w kontrolerze, aby test przeszedł!).
Daj znać jak poszło wdrożenie tego RESTa! 🌐


