package pl.edu.praktyki

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.CommandLineRunner
import org.springframework.beans.factory.annotation.Autowired
import groovy.cli.picocli.CliBuilder
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.service.*
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

@SpringBootApplication
@org.springframework.cache.annotation.EnableCaching
class SmartFinDbApp implements CommandLineRunner {

    @Autowired TransactionIngesterService ingester
    @Autowired CurrencyService currencySvc
    @Autowired FinancialAnalyticsService analyticsSvc
    @Autowired ReportGeneratorService reportSvc
    @Autowired TransactionRepository repo

    static void main(String[] args) {
        // Uruchamiamy aplikację Spring Boota
        SpringApplication.run(SmartFinDbApp, args)
    }

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
        repo.saveAll(entitiesToSave)

        // --- ODCZYT Z BAZY (Mapowanie Entity -> Domain) ---
        def dbRecords = repo.findAll()
        println ">>> W bazie znajduje się obecnie ${dbRecords.size()} transakcji."

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

        // Generujemy raport z całej historii, a nie tylko bieżącej paczki!
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