package pl.edu.praktyki

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.CommandLineRunner
import org.springframework.beans.factory.annotation.Autowired
import groovy.cli.picocli.CliBuilder
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.service.*
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate
import org.springframework.scheduling.annotation.EnableScheduling
import pl.edu.praktyki.facade.SmartFinFacade // <-- wzorzec Facade
import pl.edu.praktyki.parser.TransactionParser
import pl.edu.praktyki.parser.ParserFactory

// 1. GŁÓWNA KLASA (Tylko startuje Spring Boota, nic więcej)
@SpringBootApplication
@EnableCaching
@EnableScheduling
class SmartFinDbApp {
    static void main(String[] args) {
        SpringApplication.run(SmartFinDbApp, args)
    }
}


// 2. KLASA URUCHOMIENIOWA CLI  (od nowa wersja z Fasadą)
@Component
@Profile("!test") // <-- MAGIA: Uruchomi się zawsze, CHYBA ŻE aktywny jest profil "test"
class SmartFinCliRunner implements CommandLineRunner {

    // Zamiast 6 serwisów, wstrzykujemy tylko JEDNĄ Fasadę!
    // Na podstawie klasy SmartFinCliRunner_invalidated (linie 71–76),
    // oto 6 serwisów, które zostały zastąpione jedną fasadą:
    // 1. TransactionIngesterService (linia 71) – ingester do przetwarzania i tagowania transakcji
    // 2. CurrencyService (linia 72) – pobieranie kursów walut
    // 3. FinancialAnalyticsService (linia 73) – obliczanie bilansu, kategorii wydatków
    // 4. ReportGeneratorService (linia 74) – generowanie raportu miesięcznego
    // 5. FinanceMetrics (linia 76) – aktualizacja metryk monitoringu (bilans na "tablicy LED")
    // 6. TransactionRepository (linia 75), czyli technicznie wstrzykiwano 6 zależności
    // Wszystkie te zależności zostały ukryte za SmartFinFacade (linia 36),
    // która eksponuje jedną metodę processAndGenerateReport().

    @Autowired SmartFinFacade smartFinFacade
    @Autowired CurrencyService currencySvc // Zostawiamy tylko do walidacji początkowej waluty

    @Override
    void run(String... args) {
        def cli = new CliBuilder(usage: 'smart-fin-db -u <user> [-c <currency>]')
        cli.with {
            u longOpt: 'user', args: 1, required: true, 'Imię i nazwisko użytkownika'
            c longOpt: 'currency', args: 1, 'Waluta bazowa (domyślnie PLN)'
            f longOpt: 'file', args: 1, required: true, 'Ścieżka do pliku CSV lub JSON'
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
        def myFile = new File(opts.f as String)
        if (!myFile.exists()) {
            System.err.println "BŁĄD: Plik ${myFile.path} nie istnieje."
            return
        }

        // z nazwy pliku wybieramy odpowiedni parser (CSV, JSON, XML, itp.)
        // To jest miejsce, gdzie wzorzec FASADA naprawdę błyszczy
        //        – zamiast rozpraszać logikę wyboru parsera po całej klasie
        // Wzorzec Strategy + DIP: fabryka dobiera parser (CSV/JSON) na podstawie rozszerzenia
        TransactionParser parser = ParserFactory.getParserForFile(myFile)

        List<Transaction> rawData = parser.parse(myFile)
        /*
        def rawData = [
                new Transaction(id: "1", amount: 100, currency: "EUR", category: "Jedzenie", description: "Obiad", date: LocalDate.now()),
                new Transaction(id: "2", amount: -50, currency: "USD", category: "Rozrywka", description: "Kino", date: LocalDate.now()),
                new Transaction(id: "3", amount: 2000, currency: "PLN", category: "Praca", description: "Bonus", date: LocalDate.now())
        ] */

        rawData.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency)
            tx.amountPLN = tx.amount * rate
        }

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







//  Now invalidated this code  - ponieważ wdrożyliśmy wzorzec FASADY - smartFinFacade.groovy.
//  Zamiast rozpraszać logikę po całej klasie CLI, przenieśliśmy ją do jednej, zgrabnej metody w fasadzie.
//  Teraz CLI jest super czyste i skupia się tylko na interakcji z użytkownikiem,
//  a cała złożoność przetwarzania danych jest ukryta za fasadą.
//  Stary kod pozostawiamy zakomentowany, aby pokazać różnicę i dla porównania z wersją fasadową.
//  ale zostawiamy ją w repozytorium jako punkt odniesienia i dla porównania z wersją fasadową.
// 2. KLASA URUCHOMIENIOWA CLI
@Component
@Profile("!test") // <-- MAGIA: Uruchomi się zawsze, CHYBA ŻE aktywny jest profil "test"
class SmartFinCliRunner_invalidated implements CommandLineRunner {

    @Autowired TransactionIngesterService ingester
    @Autowired CurrencyService currencySvc
    @Autowired FinancialAnalyticsService analyticsSvc
    @Autowired ReportGeneratorService reportSvc
    @Autowired TransactionRepository repo
    @Autowired pl.edu.praktyki.monitoring.FinanceMetrics financeMetrics

    @Override
    void run(String... args) {
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

        def rawData = [
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

        // --- ZAPIS DO BAZY (Mapowanie Domain -> Entity) ---
        // 1. Wrzucamy dzisiejsze dokumenty do Sejfu
        println ">>> Zapisywanie do bazy H2..."
        def entitiesToSave = rawData.collect { tx ->
            new TransactionEntity(
                    originalId: tx.id,
                    date: tx.date,
                    amount: tx.amount,
                    currency: tx.currency,
                    amountPLN: tx.amountPLN,
                    category: tx.category,
                    description: tx.description,
                    tags: tx.tags
            )
        }
        println ">>> Zapisywanie do bazy H2..."
        repo.saveAll(entitiesToSave)

        // --- ODCZYT Z BAZY (Mapowanie Entity -> Domain) ---
        // 2. Pobieramy wszystkie historyczne wpisy z bazy (to są obiekty TransactionEntity)
        //    Wyciągamy wszystko z Sejfu (w koszulkach)
        def dbRecords = repo.findAll()
        println ">>> W bazie znajduje się obecnie ${dbRecords.size()} transakcji."

        // Zamieniamy TransactionEntity z powrotem na zwykłe Transaction
        // 3. Przepisujemy z koszulek (Entity) na zwykłe kartki (Transaction)
        def allHistory = dbRecords.collect { ent ->
            new Transaction(
                    id: ent.originalId,
                    date: ent.date,
                    amount: ent.amount,
                    currency: ent.currency,
                    amountPLN: ent.amountPLN,
                    category: ent.category,
                    description: ent.description,
                    tags: ent.tags
            )
        }

        // 4. Wyliczamy aktualny bilans całej historii (suma wszystkich tarnsakcji w PLN)
        //    Księgowy liczy całkowity bilans z czystych kartek
        def currentBalance = analyticsSvc.calculateTotalBalance(allHistory)

        // 5. Mówimy metrykom: "Hej, zaktualizuj wskaźnik, nowy bilans to currentBalance"
        //    Wpisujemy wynik na Tablicę LED na ścianie
        financeMetrics.updateBalance(currentBalance)

        // 6. Generujemy raport z całej historii, a nie tylko bieżącej paczki!
        def stats = [
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