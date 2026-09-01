package pl.edu.praktyki.event

import java.time.OffsetDateTime

class OperationBatchProcessedEvent {
    String trigger // np. ALL, DEPOSIT, WITHDRAWAL...
    int total
    int saved
    int skipped
    int failed
    OffsetDateTime processedAt = OffsetDateTime.now()
}