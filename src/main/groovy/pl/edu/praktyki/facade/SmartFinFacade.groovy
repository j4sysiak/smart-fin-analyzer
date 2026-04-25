package pl.edu.praktyki.facade

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.service.*
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.CategoryRepository
import pl.edu.praktyki.repository.CategoryEntity
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


    //Odpowiada za wczytanie i normalizację transakcji z różnych źródeł oraz zastosowanie reguł biznesowych/filtrów.
    // Typowe metody: ingest(...), applyRules(...).
    // Zwraca ustrukturyzowaną listę Transaction.
    @Autowired TransactionIngesterService ingester

    // Dostarcza kursy walut i konwersje (np. getExchangeRate(currency)).
    // Powinien być lekki i bezstanowy; w praktyce może cache’ować kursy i korzystać z zewnętrznych API.
    @Autowired CurrencyService currencySvc

    // Realizuje obliczenia statystyczne i analitykę nad historią transakcji: calculateTotalBalance(...), getTopSpendingCategory(...), getSpendingByCategory(...).
    // Powinien operować na kolekcjach i zwracać wartości/strukturę wyników.
    @Autowired FinancialAnalyticsService analyticsSvc

    // Generuje raporty (tu: generateMonthlyReport(userName, stats)) z dostarczonych danych/metriców.
    // Odpowiedzialny za formatowanie treści raportu (tekst, HTML, PDF itp.).
    @Autowired ReportGeneratorService reportSvc

    // Interfejs dostępu do bazy (zwykle Spring Data*Repository).
    // Operacje CRUD, findAll(), save(...) itd. Mapuje TransactionEntity do tabeli DB.
    @Autowired TransactionRepository repo
    @Autowired CategoryRepository categoryRepository

    // Delegat odpowiedzialny za hurtowy zapis encji w transakcji.
    // Powinien być oznaczony jako osobny bean z @Transactional, aby AOP proxy zadziałało (stąd wyodrębnienie poza fasadę).
    // Typowe metody: saveAllInTransaction(List<TransactionEntity>).
    @Autowired TransactionBulkSaver bulkSaver

    // Publikator zdarzeń Springa (Nadajnik Springa)
    // pozwala publikować zdarzenia do systemu np. do `TransactionBatchProcessedEvent`.
    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired ThreadTracker threadTracker


    /**
     * NOWOŚĆ: Asynchroniczne procesowanie.
     * Metoda kończy się natychmiast, a praca leci w tle na wątku z puli 'bulkTaskExecutor'.
     * zawsze musi byc na zwrotce void, bo @Async nie obsługuje zwracania wartości (Future/CompletableFuture to inna historia).
     */
    // Adnotacje takie jak @Async czy @Transactional tworzą "opakowanie" wokół Twojej klasy.
    // Chodzi o klasę będącą beanem Springa — czyli klasę zarządzaną przez kontener
    // (np. oznaczoną @Component, @Service, @Repository, @Configuration albo zdefiniowaną jako @Bean).
    // Adnotacje takie jak @Async czy @Transactional działają przez utworzenie proxy wokół tego beana
    // i przechwytywanie wywołań metod przychodzących z zewnątrz.

    // Włącza obsługę adnotacji @Async.
    // Dzięki temu możesz oznaczać metody jako asynchroniczne, a Spring będzie je wykonywał w osobnych wątkach.
    // a co tu ma ten bulkTaskExecutor?
    // To nazwa puli wątków, którą musisz zdefiniować w konfiguracji Springa
    // (np. @EnableAsync + @Bean(name = "bulkTaskExecutor") Executor ...).
    // Metoda kończy się natychmiast, a praca leci w tle na wątku z puli 'bulkTaskExecutor'.

    // Ta metoda tylko do wywolań bachowanych z zewnątrz (np. z CLI, REST, GUI)
    // - wewnętrzne wywołania  idą do metody niżej niesynchronizowanej: processAndGenerateReport(...)
    @Async("bulkTaskExecutor") // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
    void processInBackgroundTask(String userName, List<Transaction> rawTransactions, List<String> rules) {
        log.info(">>> [ASYNC] Rozpoczynam ciężką pracę w tle dla: {}", userName)

        // Zapisujemy informacje o wątku/ts i liczbie transakcji — przydatne w testach i diagnostyce
        threadTracker.put('SmartFinFacade.processInBackgroundTask', [
                thread: Thread.currentThread().name,
                ts: System.currentTimeMillis(),
                user: userName,
                count: rawTransactions.size()
        ])

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
            // Resolve category name (String) to CategoryEntity. If missing, create a minimal one.
            CategoryEntity categoryEntity = null
            try {
                if (tx.category) {
                    categoryEntity = categoryRepository.findByName(tx.category).orElseGet({
                        // create a new category with default monthlyLimit = 0.0
                        categoryRepository.save(new CategoryEntity(name: tx.category, monthlyLimit: 0.0))
                    })
                }
            } catch (Exception e) {
                log.warn(">>> [FASADA] Nie udało się rozwiązać kategorii '{}': {}", tx.category, e.message)
            }

            new TransactionEntity(
                    originalId: tx.id,
                    date: tx.date,
                    amount: tx.amount,
                    currency: tx.currency,
                    amountPLN: tx.amountPLN,
                    category: categoryEntity,
                    description: tx.description,
                    tags: tx.tags
            )
        }
        // ... and Delegate to a separate transactional bean so Spring AOP proxy applies
        try {
            log.info(">>> [FASADA] Zapisuję {} encji do bazy (deleguję do bulkSaver)", entities.size())
            bulkSaver.saveAllInTransaction(entities)
            // Po zapisie logujemy liczebność z repozytorium - pomoże nam zdiagnozować problemy z transakcjami
            try {
                log.info(">>> [FASADA] Po zapisie repo.count() = {}", repo.count())
            } catch (Exception e) {
                log.warn(">>> [FASADA] Nie udało się pobrać repo.count(): {}", e.message)
            }
        } catch (Exception ex) {
            // Logujemy błąd, ale wyrzucamy dalej - klient powinien dostać 500 (jeśli coś pójdzie nie tak)
            log.error(">>> [FASADA] Błąd podczas zapisu encji: {}", ex.message, ex)
            throw ex
        }


        // 4. Odczyt historii
        def allHistory = repo.findAll().collect { ent ->
            new Transaction(
                    id: ent.originalId,
                    date: ent.date,
                    amount: ent.amount,
                    currency: ent.currency,
                    amountPLN: ent.amountPLN,
                    category: (ent.category instanceof CategoryEntity) ? ent.category.name : ent.category,
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

        // ==============================================================================
        // NOWOŚĆ: ASYNCHRONICZNE POWIADOMIENIE (Side Effect) - PUBLIKACJA TWOJEGO EVENTU
        // Wysyłamy informację o sukcesie, nie czekając na to, co zrobią słuchacze.
        // 3. PUBLIKACJA TWOJEGO EVENTU
        // Używamy klasy `TransactionBatchProcessedEvent` z polami:
        //    - userName
        //    - totalBalance
        //    - transactionsCount
        //    - generatedReport
        // ===============================================================================
        eventPublisher.publishEvent(new TransactionBatchProcessedEvent(
                userName: userName,
                totalBalance: stats.totalBalance,
                transactionsCount: flatListOfTransactions?.size() ?: 0,
                generatedReport: finalReport
        ))

        log.info(">>> [FASADA] Przetwarzanie zakończone. Zwracam raport do klienta.")
        return finalReport
    }
}