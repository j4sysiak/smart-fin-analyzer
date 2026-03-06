package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import groovy.util.logging.Slf4j
import pl.edu.praktyki.repository.TransactionRepository

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
        def totalPln = allTx*.amountPLN.sum()

        log.info("Obecny stan systemu: {} zapisanych transakcji.", count)
        log.info("Całkowity bilans użytkowników: {} PLN", totalPln)
        log.info("==================================================================")
    }
}