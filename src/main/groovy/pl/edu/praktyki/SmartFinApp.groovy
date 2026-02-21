package pl.edu.praktyki

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import groovy.cli.picocli.CliBuilder
import pl.edu.praktyki.service.*
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

@Configuration
@ComponentScan(basePackages = "pl.edu.praktyki.service")
class SmartFinConfig {}

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

        // Pobieramy potrzebne serwisy
        //  To jest ręczne wstrzykiwanie zależności (dependency injection) — zamiast używać adnotacji @Autowired, bean jest pobierany programowo,
        //  ponieważ SmartFinApp nie jest zarządzany przez Springa (ma statyczną metodę main).
        def ingester = ctx.getBean(TransactionIngesterService)
        def currencySvc = ctx.getBean(CurrencyService)
        def analyticsSvc = ctx.getBean(FinancialAnalyticsService)
        def reportSvc = ctx.getBean(ReportGeneratorService)

        // 3. Symulacja pobrania danych (w realnym systemie tu byłby odczyt z pliku)
        println ">>> Importowanie danych..."
        def rawData = [
                new Transaction(id: "1", amount: 100, currency: "EUR", category: "Jedzenie", description: "Obiad w Berlinie"),
                new Transaction(id: "2", amount: -50, currency: "USD", category: "Rozrywka", description: "Kino NY"),
                new Transaction(id: "3", amount: 2000, currency: "PLN", category: "Praca", description: "Bonus")
        ]

        // 4. PROCESOWANIE (Rurociąg)
        println ">>> Przeliczanie walut i analiza..."

        // Pobieramy kursy i przeliczamy (Normalizacja)
        rawData.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency)
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