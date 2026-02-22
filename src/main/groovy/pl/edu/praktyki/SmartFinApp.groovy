package pl.edu.praktyki

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import groovy.cli.picocli.CliBuilder
import pl.edu.praktyki.service.*
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

@Configuration
@ComponentScan(basePackages = "pl.edu.praktyki.service")
@EnableCaching // <-- DODAJ TO
class SmartFinConfig {}

@EnableCaching // <-- DODAJ TO
class SmartFinApp {

    static void main(String[] args) {
        // 1. Konfiguracja CLI
        def cli = new CliBuilder(usage: 'smart-fin -u <user> [-c <currency>]')
        cli.with {
            u longOpt: 'user', args: 1, required: true, 'Imię i nazwisko użytkownika'
            c longOpt: 'currency', args: 1, 'Waluta bazowa (domyślnie PLN)'
            h longOpt: 'help', 'Pokaż pomoc'
        }

        def opts = cli.parse(args)
        if (!opts || opts.h) return

        println ">>> Inicjalizacja systemu Smart-Fin-Analyzer..."

        // 2. Uruchomienie kontekstu Springa
        // Użyjemy tutaj AnnotationConfigApplicationContext,
        // aby "odpalić" Springa wewnątrz zwykłego skryptu Groovy i uzyskać dostęp do wszystkich naszych serwisów.
        def ctx = new AnnotationConfigApplicationContext(SmartFinConfig)

        //  Pobieramy potrzebne serwisy
        //  To jest ręczne wstrzykiwanie zależności (dependency injection) — zamiast używać adnotacji @Autowired, bean jest pobierany programowo,
        //  ponieważ SmartFinApp nie jest zarządzany przez Springa (ma statyczną metodę main).
        def ingester = ctx.getBean(TransactionIngesterService)
        def currencySvc = ctx.getBean(CurrencyService)
        def analyticsSvc = ctx.getBean(FinancialAnalyticsService)
        def reportSvc = ctx.getBean(ReportGeneratorService)

        // 3. Symulacja pobrania danych (w realnym systemie tu byłby odczyt z pliku)
        println ">>> Importowanie danych..."
        def rawData = [
                new Transaction(id: "1", amount: -100, currency: "EUR", category: "Jedzenie", description: "Obiad w Berlinie"),
                new Transaction(id: "2", amount: -100, currency: "EUR", category: "Jedzenie", description: "Obiad w Berlinie2"),
                new Transaction(id: "3", amount: -50, currency: "USD", category: "Rozrywka", description: "Kino NY"),
                new Transaction(id: "4", amount: 2000, currency: "PLN", category: "Praca", description: "Bonus")
        ]

        // 4. PROCESOWANIE (Rurociąg)
        println ">>> Przeliczanie walut i analiza..."


        // Pobieramy kursy i przeliczamy (Normalizacja)
        def baseCurrency = opts.c ?: 'PLN'
        println ">>> Waluta bazowa: $baseCurrency"
        def rateTo = currencySvc.getExchangeRate(baseCurrency)

        if (rateTo == null) {
            System.err.println "BŁĄD: Waluta bazowa '$baseCurrency' nie jest obsługiwana przez system."
            ctx.close()
            return // Zatrzymujemy aplikację, bo nie mamy punktu odniesienia
        }

        println ">>> Waluta bazowa: $baseCurrency (kurs: ${1/rateTo})"
        println ">>> Przeliczanie transakcji..."

        rawData.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency)

            if (rate == null) {
                // Obsługa błędu dla konkretnej transakcji (np. ktoś wpisał 'ZŁ' zamiast 'PLN')
                println ">>> [OSTRZEŻENIE] Nieznana waluta '${tx.currency}' w transakcji ${tx.id}. Używam kursu 1.0"
                rateFrom = 1.0
            }

            // Twoja logika przeliczania:
            // (amount * rateFrom) zamienia na PLN, a dzielenie przez rateTo zamienia na walutę docelową
            tx.amountPLN = tx.amount * rate
        }
        
        // Aplikujemy reguły (używamy reguł zdefiniowanych na sztywno dla przykładu)
        def rules = ["if (amountPLN < -100) addTag('BIG_SPENDER')"]
        ingester.ingestAndApplyRules([rawData], rules)

        // 5. ANALITYKA I RAPORT
        def stats = [
                totalBalance: analyticsSvc.calculateTotalBalance(rawData),
                topCategory: analyticsSvc.getTopSpendingCategory(rawData),
                spendingMap: analyticsSvc.getSpendingByCategory(rawData)
        ]

        String report = reportSvc.generateMonthlyReport(opts.u, stats)

        // 6. WYJŚCIE
        println "\n" + report

        def fileName = "report_${opts.u.replace(' ', '_')}.txt"
        new File(fileName).text = report
        println ">>> Raport został zapisany w pliku: $fileName"

        ctx.close()
    }
}