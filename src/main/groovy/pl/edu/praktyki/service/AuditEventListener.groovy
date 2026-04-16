package pl.edu.praktyki.service

import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import groovy.util.logging.Slf4j

@Service
@Slf4j
class AuditEventListener {

    @Async("bulkTaskExecutor") // Robimy to w tle!
    @EventListener
    void onBatchProcessed(TransactionBatchProcessedEvent event) {
        log.info(">>> [AUDIT] Użytkownik {} właśnie zaimportował {} transakcji.",
                event.userName, event.transactionsCount)
        // Tutaj moglibyśmy zapisać informację do bazy audytowej
    }
}