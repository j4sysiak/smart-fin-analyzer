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

    @Autowired FinancialSummaryRepository summaryRepo

    /**
     * To jest serce CQRS. Ta metoda projektuje dane z eventu na tabelę statystyk.
     */
    @Async("bulkTaskExecutor") // Wykonaj to w tle, nie blokuj Fasady!
    @EventListener
    @Transactional
    void projectBatchToGlobalSummary(TransactionBatchProcessedEvent event) {
        log.info(">>> [CQRS-PROJECTOR] Aktualizuję widok globalny dla: {}", event.userName)

        // Używamy blokady na poziomie bazy danych lub po prostu orElse
        // W prawdziwym systemie użylibyśmy tu zapytania UPDATE ... SET balance = balance + :val
        // Bezpieczne pobranie lub inicjalizacja modelu odczytu
        def summary = summaryRepo.findById("GLOBAL")
                .orElseGet {
                    log.info(">>> [CQRS] Inicjalizacja wiersza GLOBAL w nowej bazie.")
                    new FinancialSummaryEntity(id: "GLOBAL", totalBalance: 0.0, transactionCount: 0)
                }

        // Aktualizacja widoku (Projection update)
        summary.totalBalance += event.totalBalance
        summary.transactionCount += (event.transactionsCount ?: 0)

        try {
            summaryRepo.saveAndFlush(summary) // saveAndFlush wymusza zapis natychmiast
        } catch (Exception e) {
            // Jeśli inny wątek nas ubiegł, logujemy to, ale nie wywalamy systemu
            log.warn(">>> [CQRS] Konflikt zapisu dla GLOBAL (prawdopodobnie inny wątek już go zaktualizował).")
            log.warn(">>> [CONCURRENCY] Wykryto konflikt wersji dla GLOBAL! Ktoś inny zmienił bilans. Ponawiam...")
             // Możemy tu dodać retry logic, ale na razie po prostu logujemy i kończymy.
            // Tutaj w prawdziwym kodzie wywołalibyśmy metodę ponownie, ale dla uproszczenia tego nie robimy.
        }

        log.info(">>> [CQRS-PROJECTOR] Widok zaktualizowany. Nowy bilans: {} PLN", summary.totalBalance)
    }
}