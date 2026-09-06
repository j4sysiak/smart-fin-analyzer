package pl.edu.praktyki.operation

import groovy.util.logging.Slf4j
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import pl.edu.praktyki.event.OperationBatchProcessedEvent

import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Service
@Slf4j
class OperationBatchAuditListener {

    private final AtomicInteger processedEventsCount = new AtomicInteger(0)
    private final AtomicReference<String> lastTrigger = new AtomicReference<>(null)
    private final AtomicReference<Map<String, Object>> lastSummary = new AtomicReference<>(null)

    private static final int MAX_HISTORY_SIZE = 100
    private final java.util.Deque<Map<String, Object>> history = new java.util.concurrent.ConcurrentLinkedDeque<>()

    int getProcessedCount() {
        processedEventsCount.get()
    }

    String getLastTrigger() {
        lastTrigger.get()
    }

    Map<String, Object> getLastSummary() {
        lastSummary.get()
    }

    boolean hasLastSummary() {
        lastSummary.get() != null
    }

    void reset() {
        processedEventsCount.set(0)
        lastTrigger.set(null)
        lastSummary.set(null)
    }

    @Async("bulkTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onBatchProcessed(OperationBatchProcessedEvent event) {
        log.info(">>> [OPERATIONS-AUDIT] trigger={}, total={}, saved={}, skipped={}, failed={}",
                event.trigger, event.total, event.saved, event.skipped, event.failed)

        lastTrigger.set(event.trigger)
        lastSummary.set([
                trigger    : event.trigger,
                total      : event.total,
                saved      : event.saved,
                skipped    : event.skipped,
                failed     : event.failed,
                processedAt: event.processedAt ?: OffsetDateTime.now()
        ].asImmutable())

        history.addFirst(lastSummary.get())
        while (history.size() > MAX_HISTORY_SIZE) {
            history.pollLast()
        }

        processedEventsCount.incrementAndGet()
    }

    //bez trigger zwraca normalną historię,
    //z trigger=DEPOSIT zwraca tylko wpisy DEPOSIT.
    List<Map<String, Object>> getHistory(int limit, String trigger) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_HISTORY_SIZE))
        String normalizedTrigger = trigger?.trim()?.toUpperCase()

        def stream = history.stream()
        if (normalizedTrigger) {
            stream = stream.filter { Map<String, Object> item ->
                (item.trigger as String)?.toUpperCase() == normalizedTrigger
            }
        }

        return stream.limit(safeLimit).collect(java.util.stream.Collectors.toList())
    }

    void clearHistory() {
        history.clear()
    }
}