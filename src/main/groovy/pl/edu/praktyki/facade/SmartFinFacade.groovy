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
     * To jest JEDYNA metoda, o której musi wiedzieć świat zewnętrzny będzie ją wolal: (CLI, REST, GUI).
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