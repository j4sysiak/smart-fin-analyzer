package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import pl.edu.praktyki.repository.FinancialSummaryEntity
import pl.edu.praktyki.repository.FinancialSummaryRepository
import groovy.util.logging.Slf4j

@Service
@Slf4j
class SummaryProjectionListener {

    @Autowired FinancialSummaryRepository summaryRepo

    @EventListener
    @Transactional // Bardzo ważne: aktualizacja statystyk musi być atomowa!
    void handleNewTransactions(TransactionBatchProcessedEvent event) {
        log.info(">>> [CQRS] Aktualizuję projekcję statystyk dla paczki od: {}", event.userName)

        // BEZPIECZNE POBIERANIE: Jeśli nie ma wiersza GLOBAL, stwórz go w pamięci
        def summary = summaryRepo.findById("GLOBAL").orElseGet {
            log.info(">>> [CQRS] Wiersz GLOBAL nie istniał, tworzę go...")
            return new FinancialSummaryEntity(id: "GLOBAL", totalBalance: 0.0, transactionCount: 0)
        }

        // Aktualizujemy gotowy widok
        summary.totalBalance += event.totalBalance
        summary.transactionCount += 1 // Liczymy paczki lub transakcje

        summaryRepo.save(summary)
        log.info(">>> [CQRS] Nowy bilans globalny: {}", summary.totalBalance)
    }
}