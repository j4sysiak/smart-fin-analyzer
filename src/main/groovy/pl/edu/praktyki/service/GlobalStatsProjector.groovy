package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async

// KONIECZNE DLA ETAPU 4
// Lab77--Zaawansowana-Asynchroniczność-i-Eventy--Rozprzęganie-Decoupling-za-pomocą-Spring-Events

import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import pl.edu.praktyki.event.TransactionBatchProcessedEvent

// import pl.edu.praktyki.repository.FinancialSummaryRepository

@Service
@Slf4j
class GlobalStatsProjector {

    @Autowired
    ThreadTracker threadTracker

    @Autowired
    GlobalSummaryUpdater globalSummaryUpdater

    /**
     * To jest serce CQRS. Ta metoda projektuje dane z eventu na tabelę statystyk.
     */
    @Async("bulkTaskExecutor")
    // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    // <-- To jest ważne! Oznacza, że ten event będzie obsługiwany dopiero po COMMIT'cie transakcji, która go wywołała. Dzięki temu mamy pewność, że dane są już zapisane w bazie, zanim zaczniemy projektować statystyki.
    void projectBatchToGlobalSummary(TransactionBatchProcessedEvent event) {
        log.info(">>> [CQRS-PROJECTOR] Próba blokady wiersza GLOBAL dla: {}", event.userName)

        // zapisujemy, który wątek obsługuje ostatni event
        // klucz zawiera nazwę bean'a/metody, by nie nadpisywać innych projektorów
        threadTracker.put('GlobalStatsProjector.lastThread', [thread: Thread.currentThread().name,
                                                              ts    : System.currentTimeMillis(),
                                                              user  : event?.userName,
                                                              count : event?.transactionsCount])

        // Deleguj do metody z oznaczeniem `@Transactional`, żeby mieć pewność, że cała logika aktualizacji GLOBAL będzie w jednej transakcji.
        // To jest ważne, bo chcemy mieć pewność, że blokada (w przypadku pesymistycznego podejścia) będzie trzymana przez cały czas aktualizacji.
        globalSummaryUpdater.updateGlobalSummary(event)

        log.info(">>> [CQRS-PROJECTOR] Zakończono aktualizację GLOBAL dla: {}", event.userName)
    }
}