package pl.edu.praktyki.facade

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.service.*
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.domain.Transaction
import groovy.util.logging.Slf4j
import org.springframework.scheduling.annotation.Async
import org.springframework.context.ApplicationEventPublisher // DODAJ IMPORT
import pl.edu.praktyki.event.TransactionBatchProcessedEvent // DODAJ IMPORT

@Service
@Slf4j
class SmartFinFacade {

    // Fasada ukrywa w sobie całą złożoność podsystemu (wstrzykuje 5 różnych klas!)
    @Autowired TransactionIngesterService ingester
    @Autowired CurrencyService currencySvc
    @Autowired FinancialAnalyticsService analyticsSvc
    @Autowired ReportGeneratorService reportSvc
    @Autowired TransactionRepository repo
    @Autowired TransactionBulkSaver bulkSaver
    @Autowired ApplicationEventPublisher eventPublisher // Publikator zdarzeń Springa






    /**
     * NOWOŚĆ: Asynchroniczne procesowanie.
     * Metoda kończy się natychmiast, a praca leci w tle na wątku z puli 'bulkTaskExecutor'.
     * zawsze musi byc na zwrotce void, bo @Async nie obsługuje zwracania wartości (Future/CompletableFuture to inna historia).
     */
    // Adnotacje takie jak @Async czy @Transactional tworzą "opakowanie" wokół Twojej klasy.
    @Async("bulkTaskExecutor")
    void processInBackgroundTask(String userName, List<Transaction> rawTransactions, List<String> rules) {
        log.info(">>> [ASYNC] Rozpoczynam ciężką pracę w tle dla: {}", userName)

        // Wywołujemy Twoją potężną logikę zapisu
        // (Tu wywołaj logikę, którą miałeś w Facade - przeliczanie, reguły i na końcu Twój BulkSaver)
        def report = processAndGenerateReport(userName, rawTransactions, rules)
        log.info(">>> [FASADA] Przetwarzanie zakończone.) Generuję raport. " + report)
        log.info(">>> [ASYNC] Praca w tle zakończona pomyślnie.")
    }







    /**   F A S A D A   - ukrywa złożoność, oferuje prosty interfejs do świata zewnętrznego.
     * To jest JEDYNA metoda, o której musi wiedzieć świat zewnętrzny będzie ją wolal: (CLI, REST, GUI).
     */
    // Metoda wykonuje synchronizowane przetworzenie i zwraca raport.
    // UWAGA: nie oznaczamy jej jako @Async, ponieważ zwraca String (asynchroniczne metody
    // powinny zwracać void lub Future/CompletableFuture). Asynchroniczne uruchamianie
    // odbywa się przez metodę processInBackgroundTask, która wywołuje tę metodę wewnętrznie.
    String processAndGenerateReport(String userName, List<Transaction> rawTransactions, List<String> rules) {
        log.info(">>> [FASADA] Rozpoczynam kompleksowe przetwarzanie dla użytkownika: {}", userName)
        log.info(">>> [ASYNC] Rozpoczynam (dotyczy testu EventDecouplingSpec) ciężką pracę w tle dla: {}", userName)

        // 1. Przeliczanie walut
        rawTransactions.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency ?: "PLN")
            tx.amountPLN = tx.amount * rate
        }


        // 2. Reguły i Import
        List<Transaction> flatListOfTransactions = ingester.ingestAndApplyRules([rawTransactions], rules)


        // 3. Zapis do bazy (Mapowanie)
        def entities = flatListOfTransactions.collect { tx ->
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
        // ... and Delegate to a separate transactional bean so Spring AOP proxy applies
        bulkSaver.saveAllInTransaction(entities)


        // 4. Odczyt historii
        def allHistory = repo.findAll().collect { ent ->
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


        // 5. Analityka
        def stats =[
                totalBalance: analyticsSvc.calculateTotalBalance(allHistory),
                topCategory: analyticsSvc.getTopSpendingCategory(allHistory),
                spendingMap: analyticsSvc.getSpendingByCategory(allHistory)
        ]


        // 6. Generowanie Raportu
        String finalReport = reportSvc.generateMonthlyReport(userName, stats)

        // ========================================================================
        // NOWOŚĆ: ASYNCHRONICZNE POWIADOMIENIE (Side Effect)
        // Wysyłamy informację o sukcesie, nie czekając na to, co zrobią słuchacze.
        // ========================================================================
        eventPublisher.publishEvent(new TransactionBatchProcessedEvent(
                userName: userName,
                totalBalance: stats.totalBalance,
                generatedReport: finalReport
        ))

        log.info(">>> [FASADA] Przetwarzanie zakończone. Zwracam raport do klienta.")
        return finalReport
    }
}