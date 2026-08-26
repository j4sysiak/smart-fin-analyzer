package pl.edu.praktyki.contract.egress.outbox

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

import java.time.Instant

// To jest po prostu `Sheduler`, który cyklicznie sprawdza tabelę outboxa w bazie danych, czy są nowe wpisy do wysłania.
// "rezerwuje" je do przetworzenia (ustawiając status na PROCESSING) i deleguje do `EgressOutboxProcessora`

@Component
@Slf4j
class EgressOutboxDispatcher {

    private final EgressOutboxRepository outboxRepository
    private final EgressOutboxProcessor processor
    private final TransactionTemplate transactionTemplate

    @Value('${app.egress.outbox.enabled:true}')
    boolean enabled

    @Value('${app.egress.outbox.batch-size:50}')
    int batchSize

    EgressOutboxDispatcher(
            EgressOutboxRepository outboxRepository,
            EgressOutboxProcessor processor,
            TransactionTemplate transactionTemplate
    ) {
        this.outboxRepository = outboxRepository
        this.processor = processor
        this.transactionTemplate = transactionTemplate
    }

    // Scheduler, który co 2 sekundy sprawdza, czy są nowe wpisy do wysłania
    // czyli, prawdza w bazie danych tabela egress_outbox, czy są wpisy o statusie NEW lub RETRY, które są gotowe do wysłania (readyToSendAt <= teraz).
    // i "rezerwuje" je do przetworzenia (ustawiając status na PROCESSING) i deleguje do `EgressOutboxProcessora`
    @Scheduled(fixedDelayString = '${app.egress.outbox.poll-ms:2000}')
    void dispatch() {
        if (!enabled) return

        List<Long> ids = transactionTemplate.execute { claimBatchInTx() } ?: []
        ids.each { Long id ->
            processor.process(id)
        }
    }

    private List<Long> claimBatchInTx() {
        def now = Instant.now()
        def rows = outboxRepository.lockBatchForDispatch(
                [EgressOutboxStatus.NEW, EgressOutboxStatus.RETRY],
                now,
                PageRequest.of(0, batchSize)
        )

        rows.each { row ->
            row.status = EgressOutboxStatus.PROCESSING
            row.attemptCount = (row.attemptCount ?: 0) + 1
        }

        if (!rows.isEmpty()) {
            log.debug("EGRESS-OUTBOX | claimed {} row(s)", rows.size())
        }

        return rows.collect { it.id }
    }
}