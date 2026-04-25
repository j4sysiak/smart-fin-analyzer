package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener

// KONIECZNE DLA ETAPU 4
// Lab77--Zaawansowana-Asynchroniczność-i-Eventy--Rozprzęganie-Decoupling-za-pomocą-Spring-Events
import org.springframework.scheduling.annotation.Async

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import pl.edu.praktyki.repository.FinancialSummaryEntity
import pl.edu.praktyki.repository.FinancialSummaryRepository
import groovy.util.logging.Slf4j

@Service
@Slf4j
class GlobalStatsProjector {

    @Autowired ThreadTracker threadTracker
    @Autowired FinancialSummaryRepository summaryRepo

    /**
     * To jest serce CQRS. Ta metoda projektuje dane z eventu na tabelę statystyk.
     */
    @Async("bulkTaskExecutor") // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
    @EventListener
    @Transactional // Bardzo ważne: blokada żyje tylko wewnątrz transakcji!
    void projectBatchToGlobalSummary(TransactionBatchProcessedEvent event) {
        log.info(">>> [CQRS-PROJECTOR] Próba blokady wiersza GLOBAL dla: {}", event.userName)

        // zapisujemy, który wątek obsługuje ostatni event
        // klucz zawiera nazwę bean'a/metody, by nie nadpisywać innych projektorów
        threadTracker.put('GlobalStatsProjector.lastThread', [thread: Thread.currentThread().name,
                                                              ts: System.currentTimeMillis(),
                                                              user: event?.userName,
                                                              count: event?.transactionsCount])

        // Pobieramy z blokadą OPTYMISTIC LOCKING.
        // Używamy blokady na poziomie bazy danych lub po prostu orElse
        // W prawdziwym systemie użylibyśmy tu zapytania UPDATE ... SET balance = balance + :val
        // Bezpieczne pobranie lub inicjalizacja modelu odczytu
        // Lab 83: W tej wersji, jeśli inny wątek już zainicjalizował wiersz GLOBAL, ten wątek po prostu go pobierze i zaktualizuje. Nie ma tu blokady, więc może dojść do konfliktu zapisu, ale to jest właśnie ryzyko optymistycznego podejścia!
        // def summary = summaryRepo.findById("GLOBAL")
        //        .orElseGet {
        //            log.info(">>> [CQRS] Inicjalizacja wiersza GLOBAL w nowej bazie.")
        //            new FinancialSummaryEntity(id: "GLOBAL", totalBalance: 0.0, transactionCount: 0)
        //        }

        // 1. Pobieramy z blokadą PESYMISTYCZNĄ.
        // Jeśli inny wątek już to trzyma, ten wątek tu ZAWISNIE i poczeka grzecznie.
        // Lab87: W tej wersji, jeśli inny wątek już trzyma blokadę, ten wątek będzie czekał, aż się zwolni. To jest właśnie magia blokady pesymistycznej!
        def summary = summaryRepo.findByIdWithLock("GLOBAL")
                .orElseGet {
                    log.info(">>> [CQRS] Inicjalizacja wiersza GLOBAL w nowej bazie.")
                    new FinancialSummaryEntity(id: "GLOBAL", totalBalance: 0.0, transactionCount: 0)
        }


        // 2. Modyfikujemy dane (Projection update - mamy gwarancję, że nikt inny teraz tego nie robi)
        summary.totalBalance += event.totalBalance
        summary.transactionCount += (event.transactionsCount ?: 0)


        // 3. Zapisujemy z opcją flush, by wymusić natychmiastowy zapis i wykryć ewentualne konflikty (w przypadku optymistycznego podejścia)
        try {
            summaryRepo.saveAndFlush(summary) // saveAndFlush wymusza zapis natychmiast
        } catch (Exception e) {
            // Jeśli inny wątek nas ubiegł, logujemy to, ale nie wywalamy systemu
            log.warn(">>> [CQRS] Konflikt zapisu dla GLOBAL (prawdopodobnie inny wątek już go zaktualizował).")
            log.warn(">>> [CONCURRENCY] Wykryto konflikt wersji dla GLOBAL! Ktoś inny zmienił bilans. Ponawiam...")
            // Możemy tu dodać retry logic, ale na razie po prostu logujemy i kończymy.
            // Tutaj w prawdziwym kodzie wywołalibyśmy metodę ponownie, ale dla uproszczenia tego nie robimy.
        }

        log.info(">>> [CQRS-PROJECTOR] Blokada zwolniona. Nowy bilans: {}", summary.totalBalance)
        // Po wyjściu z metody transakcja się kończy (commit), a Postgres puszcza kolejną osobę do wiersza.
    }
}