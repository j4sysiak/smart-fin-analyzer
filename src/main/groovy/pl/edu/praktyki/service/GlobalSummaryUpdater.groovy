package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import pl.edu.praktyki.repository.FinancialSummaryEntity
import pl.edu.praktyki.repository.FinancialSummaryRepository

@Service
@Slf4j
class GlobalSummaryUpdater {

    @Autowired
    FinancialSummaryRepository summaryRepo

    /**
     * Aktualizuje bilans GLOBAL w osobnej transakcji (REQUIRES_NEW).
     * Proxy Spring zadziała bo to jest publiczna metoda w bean'ie.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void updateGlobalSummary(TransactionBatchProcessedEvent event) {
        // Cała logika z findByIdWithLock, saveAndFlush, exception handling

        // Pobieramy z blokadą OPTIMISTIC LOCKING.
        // Używamy blokady na poziomie bazy danych lub po prostu .orElse()
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
        // Lab87: W tej wersji, jeśli inny wątek już trzyma blokadę, ten wątek będzie czekał, aż się zwolni.
        // To jest właśnie magia blokady pesymistycznej!
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

        log.info(">>> [CQRS-PROJECTOR] Zakończono próbę aktualizacji GLOBAL. Zwolnienie blokady nastąpi po zakończeniu transakcji. Stan w pamięci: {}", summary.totalBalance)
        // Po wyjściu z metody transakcja się kończy (commit), a Postgres puszcza kolejną osobę do wiersza.
    }
}
