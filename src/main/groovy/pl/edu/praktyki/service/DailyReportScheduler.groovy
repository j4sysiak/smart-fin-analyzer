package pl.edu.praktyki.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import groovy.util.logging.Slf4j
import pl.edu.praktyki.repository.TransactionRepository

// MAGIA: Serwis powstanie tylko, gdy app.scheduling.enabled=true LUB gdy tej właściwości w ogóle nie ma
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@Service
@Slf4j
class DailyReportScheduler {

    @Autowired TransactionRepository repo

    // fixedRate = 10000 oznacza, że metoda odpali się co 10 sekund (dla testów).
    // Na produkcji użylibyśmy np.: @Scheduled(cron = "0 0 0 * * ?") -> codziennie o północy.
    @Scheduled(fixedRate = 10000)
    void generateAutomaticReport() {
        log.info("=== [AUTOMATYZACJA] Uruchamiam cykliczny przegląd bazy danych ===")

        long count = repo.count()
        if (count == 0) {
            log.info("Baza jest pusta. Czekam na nowe transakcje...")
            return
        }

        // Pobieramy transakcje z bazy i robimy szybką statystykę
        def allTx = repo.findAll()
        def amounts = allTx*.amountPLN.findAll { it != null }
        def totalPln = amounts ? amounts.sum() : BigDecimal.ZERO

        log.info("Obecny stan systemu: {} zapisanych transakcji.", count)
        log.info("Całkowity bilans użytkowników: {} PLN", totalPln)
        log.info("==================================================================")
    }
}