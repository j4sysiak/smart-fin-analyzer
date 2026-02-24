package pl.edu.praktyki.service

import groovy.util.logging.Slf4j // Import do logowania, jeśli chcemy użyć logów zamiast println
import org.springframework.stereotype.Service
import org.springframework.context.event.EventListener
import pl.edu.praktyki.event.TransactionImportedEvent

@Service
@Slf4j // <-- Ta adnotacja wstrzykuje logger
class TransactionAuditListener {

    @EventListener
    void onNewTransaction(TransactionImportedEvent event) {
        // Ta metoda uruchomi się SAMA, gdy ktoś wyśle zdarzenie!
        log.info('>>> [ZDARZENIE W TLE] System zauważył nową transakcję: {}', event.transaction.id)
    }
}