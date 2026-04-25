package pl.edu.praktyki.service

import org.springframework.context.event.EventListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import groovy.util.logging.Slf4j

@Service
@Slf4j
class AuditEventListener {

    @Autowired
    ThreadTracker threadTracker



    // Metoda jest wywoływana przez Springa jako słuchacz zdarzeń.
    // Adnotacja @EventListener oznacza, że gdy w kontekście aplikacji zostanie opublikowane zdarzenie typu TransactionBatchProcessedEvent,
    // Spring wywoła onBatchProcessed.
    // Adnotacja @Async("bulkTaskExecutor") powoduje wykonanie wątku z beana bulkTaskExecutor.
    //Zdarzenie jest publikowane zwykle przez:
    // - ApplicationEventPublisher.publishEvent(...)
    // - ApplicationContext.publishEvent(...)
    // Przyklad: kalasa testowa AuditEventListenerSpec, która publikuje zdarzenie w teście integracyjnym.


    @Async("bulkTaskExecutor") // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
    @EventListener
    void onBatchProcessed(TransactionBatchProcessedEvent event) {
        log.info(">>> [AUDIT] Użytkownik {} właśnie zaimportował {} transakcji.",
                event.userName, event.transactionsCount)
        // Zapisujemy informacje o wątku i czasie dla celów debugowania/monitoringu
        threadTracker.put('AuditEventListener.onBatchProcessed', [thread: Thread.currentThread().name,
                                                                  ts: System.currentTimeMillis(),
                                                                  user: event?.userName,
                                                                  count: event?.transactionsCount])
        // Tutaj moglibyśmy zapisać informację do bazy audytowej
    }
}