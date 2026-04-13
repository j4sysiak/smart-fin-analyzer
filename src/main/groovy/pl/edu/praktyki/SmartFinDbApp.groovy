package pl.edu.praktyki

import groovy.cli.picocli.CliBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.parser.ParserFactory
import pl.edu.praktyki.parser.TransactionParser
import pl.edu.praktyki.service.CurrencyService


// 1. GŁÓWNA KLASA (Tylko startuje Spring Boota, nic więcej)
@SpringBootApplication
@EnableCaching
@EnableScheduling
class SmartFinDbApp {
    static void main(String[] args) {
        def ctx = SpringApplication.run(SmartFinDbApp, args)
        try {
            def env = ctx.getEnvironment()
            def active = env.getActiveProfiles()
            if (!active) active = env.getDefaultProfiles()
            println "=== Application started with active profiles: ${active}"
            println "=== spring.datasource.url=${env.getProperty('spring.datasource.url')}"
            println "=== spring.datasource.username=${env.getProperty('spring.datasource.username')}"
            println "=== spring.jpa.hibernate.ddl-auto=${env.getProperty('spring.jpa.hibernate.ddl-auto')}"
        } catch (Exception e) {
            println "=== Unable to print datasource info: ${e.message}"
        }
    }
}


// 2. KLASA URUCHOMIENIOWA CLI  (od nowa wersja z Fasadą)
// uruchamiamy kontekst aplikacji,
// ale ta klasa będzie aktywna tylko wtedy, gdy NIE jest aktywny profil "test" (linia 34)

@Component
@Profile("!test & !tc & !local-pg")
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

    // jak chcesz zaczytać prawdziway plik:  ./gradlew runSmartFinDb -PappArgs="-u Jacek -f transakcje.csv"


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
        println ">>> Uruchamianie wersji z BAZĄ DANYCH (PostgreSQL)..."

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
        println ">>> Zaimportowano ${rawData.size()} transakcji z pliku."
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

        // Klient (nasz test) nie wie o istnieniu repozytoriów, walut ani reguł.
        // Wywołuje tylko jedną metodę, a Fasada orkiestruje resztę.
        String report = smartFinFacade.processAndGenerateReport(opts.u, rawData, rules)

        println "\n" + report
        def fileName = "db_report_${opts.u.replace(' ', '_')}.txt"
        new File(fileName).text = report
        println ">>> Raport zapisany: $fileName"
        println "=========================================\n"
    }
}