package pl.edu.praktyki.operation

import groovy.util.logging.Slf4j
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import pl.edu.praktyki.event.OperationBatchProcessedEvent
import java.util.concurrent.atomic.AtomicReference

import java.util.concurrent.atomic.AtomicInteger

@Service
@Slf4j
class OperationBatchAuditListener {

    private final AtomicInteger processedEventsCount = new AtomicInteger(0)
    private final AtomicReference<String> lastTrigger = new AtomicReference<>(null)

    int getProcessedCount() {
        processedEventsCount.get()
    }

    String getLastTrigger() {
        lastTrigger.get()
    }

    void reset() {
        processedEventsCount.set(0)
        lastTrigger.set(null)
    }

    @Async("bulkTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onBatchProcessed(OperationBatchProcessedEvent event) {
        log.info(">>> [OPERATIONS-AUDIT] trigger={}, total={}, saved={}, skipped={}, failed={}",
                event.trigger, event.total, event.saved, event.skipped, event.failed)

        lastTrigger.set(event.trigger)
        processedEventsCount.incrementAndGet()
    }
}