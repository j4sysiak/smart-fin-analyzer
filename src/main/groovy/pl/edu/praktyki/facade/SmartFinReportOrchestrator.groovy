package pl.edu.praktyki.facade

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.domain.TransactionDto
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import pl.edu.praktyki.repository.CategoryEntity
import pl.edu.praktyki.repository.CategoryRepository
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.service.CurrencyService
import pl.edu.praktyki.service.FinancialAnalyticsService
import pl.edu.praktyki.service.ReportGeneratorService
import pl.edu.praktyki.service.TransactionIngesterService

@Service
@Slf4j
class SmartFinReportOrchestrator {

    // Dostarcza kursy walut i konwersje (np. getExchangeRate(currency)).
    // Powinien być lekki i bezstanowy; w praktyce może cache’ować kursy i korzystać z zewnętrznych API.
    @Autowired
    CurrencyService currencySvc

    //Odpowiada za wczytanie i normalizację transakcji z różnych źródeł oraz zastosowanie reguł biznesowych/filtrów.
    // Typowe metody: ingest(...), applyRules(...).
    // Zwraca ustrukturyzowaną listę Transaction.
    @Autowired
    TransactionIngesterService ingester

    // Realizuje obliczenia statystyczne i analitykę nad historią transakcji: calculateTotalBalance(...), getTopSpendingCategory(...), getSpendingByCategory(...).
    // Powinien operować na kolekcjach i zwracać wartości/strukturę wyników.
    @Autowired
    FinancialAnalyticsService analyticsSvc


    // Generuje raporty (tu: generateMonthlyReport(userName, stats)) z dostarczonych danych/metriców.
    // Odpowiedzialny za formatowanie treści raportu (tekst, HTML, PDF itp.).
    @Autowired
    ReportGeneratorService reportSvc

    // Interfejs dostępu do bazy (zwykle Spring Data*Repository).
    // Operacje CRUD, findAll(), save(...) itd. Mapuje TransactionEntity do tabeli DB.
    @Autowired
    TransactionRepository repo

    @Autowired
    CategoryRepository categoryRepository

    // Delegat odpowiedzialny za hurtowy zapis encji w transakcji.
    // Powinien być oznaczony jako osobny bean z @Transactional, aby AOP proxy zadziałało (stąd wyodrębnienie poza fasadę).
    // Typowe metody: saveAllInTransaction(List<TransactionEntity>).
    @Autowired
    TransactionBulkSaver bulkSaver

    // Publikator zdarzeń Springa (Nadajnik Springa)
    // pozwala publikować zdarzenia do systemu np. do `TransactionBatchProcessedEvent`.
    @Autowired
    ApplicationEventPublisher eventPublisher


    // Metoda processAndGenerateReport(...) wykonuje synchronizowane przetworzenie i zwraca raport.
    // UWAGA: nie oznaczamy jej jako @Async, ponieważ zwraca String (asynchroniczne metody
    // powinny zwracać void lub Future/CompletableFuture).
    // Asynchroniczne uruchamianie odbywa się przez metodę `processInBackgroundTask`,
    // która wywołuje tę metodę wewnętrznie.

    // Efekt Transactional:
    // Teraz cały przepływ od ingestu, przez zapis, aż do publikacji eventu będzie się odbywał w jednej transakcji.
    // Event publish'uje się dopiero po za'commitowaniu całej metody.
    @Transactional
    String processAndGenerateReport(String userName, List<TransactionDto> rawTransactions, List<String> rules) {
        log.info(">>> [FASADA] Rozpoczynam kompleksowe przetwarzanie dla użytkownika: {}", userName)
        log.info(">>> [ASYNC] Rozpoczynam (dotyczy testu EventDecouplingSpec) ciężką pracę w tle dla: {}", userName)

        // 1. Przeliczanie walut
        rawTransactions.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency ?: "PLN")
            tx.amountPLN = tx.amount * rate
        }


        // 2. Reguły i Import
        List<TransactionDto> flatListOfTransactions = ingester.ingestAndApplyRules([rawTransactions], rules)


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
                    categoryEntity: categoryEntity,
                    category: categoryEntity?.name,
                    description: tx.description,
                    tags: tx.tags,
                    ownerUsername: userName
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
        // UWAGA: używamy ent.categoryName (surowe pole @Column String) zamiast ent.category,
        // ponieważ getCategory() zwraca lazy-proxy Hibernate (CategoryEntity), które nie może
        // być zainicjalizowane poza sesją JPA (LazyInitializationException).
        def allHistory = repo.findAll().collect { ent ->
            new TransactionDto(
                    id: ent.originalId,
                    date: ent.date,
                    amount: ent.amount,
                    currency: ent.currency,
                    amountPLN: ent.amountPLN,
                    category: ent.categoryName,  // <--------- tutaj używamy categoryName, żeby uniknąć problemów z LazyInitializationException
                    description: ent.description,
                    tags: ent.tags
            )
        }


        // 5. Analityka
        def stats = [
                totalBalance: analyticsSvc.calculateTotalBalance(allHistory),
                topCategory : analyticsSvc.getTopSpendingCategory(allHistory),
                spendingMap : analyticsSvc.getSpendingByCategory(allHistory)
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
