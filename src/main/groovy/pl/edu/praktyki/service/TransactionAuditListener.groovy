package pl.edu.praktyki.service

import groovy.util.logging.Slf4j // Import do logowania, jeśli chcemy użyć logów zamiast println
import org.springframework.stereotype.Service
import org.springframework.context.event.EventListener
import pl.edu.praktyki.event.TransactionImportedBatchEvent

@Service
@Slf4j
class TransactionAuditListener {

    @EventListener
    void onNewTransaction(TransactionImportedBatchEvent event) {
        // Odbieramy całą paczkę transakcji i możemy je przetworzyć zbiorczo.
        // log.info('>>> [BATCH EVENT] Otrzymano paczkę {} transakcji', event.transactions?.size())
    }
}