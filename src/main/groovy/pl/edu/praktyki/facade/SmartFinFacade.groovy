package pl.edu.praktyki.facade

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.TransactionDto
import pl.edu.praktyki.service.ThreadTracker
import pl.edu.praktyki.operation.BatchOperationService


@Service
@Slf4j
class SmartFinFacade {
    // Fasada ukrywa w sobie całą złożoność podsystemu (wstrzykuje 5 różnych klas!)
    // to jest lekkim entry-pointem (sync i async) do świata zewnętrznego (CLI, REST, GUI)
    //         - to jest JEDYNA metoda, o której musi wiedzieć świat zewnętrzny będzie ją wołał np: (CLI, REST, GUI).

    @Autowired
    ThreadTracker threadTracker

    @Autowired
    SmartFinReportOrchestrator reportOrchestrator

    @Autowired
    BatchOperationService batchOperationService

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
    @Async("bulkTaskExecutor")
    // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
    void processInBackgroundTask(String userName, List<TransactionDto> rawTransactions, List<String> rules) {
        log.info(">>> [ASYNC] Rozpoczynam ciężką pracę w tle dla: {}", userName)

        // Zapisujemy informacje o wątku/ts i liczbie transakcji — przydatne w testach i diagnostyce
        threadTracker.put('SmartFinFacade.processInBackgroundTask', [
                thread: Thread.currentThread().name,
                ts    : System.currentTimeMillis(),
                user  : userName,
                count : rawTransactions.size()
        ])

        // Wywołujemy mega potężną logikę zapisu
        // (Tu wywołaj logikę, którą miałeś w Facade:
        //  1. Przeliczanie walut
        //  2. Reguły i Import
        //  3. Zapis do bazy (Mapowanie):  bulkSaver.saveAllInTransaction(entities)
        //  4. Odczyt historii
        //  5. Analityka
        //  6. Generowanie Raportu:  eventPublisher.publishEvent(new TransactionBatchProcessedEvent(...))  wysyłamy event z informacją o sukcesie, nie czekając na to, co zrobią słuchacze.
        def report = reportOrchestrator.processAndGenerateReport(userName, rawTransactions, rules)

        log.info(">>> [FASADA] Przetwarzanie zakończone.) Generuję raport. " + report)
        log.info(">>> [ASYNC] Praca w tle zakończona pomyślnie.")
    }


    /**   F A S A D A   - ukrywa złożoność, oferuje prosty interfejs do świata zewnętrznego.
     * To jest JEDYNA metoda, o której musi wiedzieć świat zewnętrzny będzie ją wołał np: (CLI, REST, GUI).
     */
    String processAndGenerateReport(String userName, List<TransactionDto> rawTransactions, List<String> rules) {
        log.info(">>> [FASADA] Rozpoczynam kompleksowe przetwarzanie dla użytkownika: {}", userName)
        log.info(">>> [ASYNC] Rozpoczynam (dotyczy testu EventDecouplingSpec) ciężką pracę w tle dla: {}", userName)

        return reportOrchestrator.processAndGenerateReport(userName, rawTransactions, rules)
    }

    Map processOperationsBatch(String operationType) {
        String raw = operationType?.trim()
        if (!raw) {
            log.info(">>> [FASADA-BATCH] Start processAll()")
            return batchOperationService.processAll()
        }

        log.info(">>> [FASADA-BATCH] Start processType({})", raw)
        return batchOperationService.processType(raw)
    }

}