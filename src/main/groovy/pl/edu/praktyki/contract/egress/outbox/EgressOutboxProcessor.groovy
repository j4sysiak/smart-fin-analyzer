package pl.edu.praktyki.contract.egress.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import java.time.Instant

// ta klasa jest odpowiedzialna za przetwarzanie pojedynczego wpisu z tabeli outboxa (tabela: egress_outbox),
// czyli próbuje dostarczyć payload do docelowego systemu (np. wysłać HTTP POST do innego serwisu).
@Service
@Slf4j
class EgressOutboxProcessor {

    private final EgressOutboxRepository outboxRepository
    private final EgressDecisionDeliveryService deliveryService
    private final ObjectMapper objectMapper
    private final MeterRegistry meterRegistry

    @Value('${app.egress.outbox.max-attempts:7}')
    int maxAttempts

    @Value('${app.egress.outbox.base-delay-ms:2000}')
    long baseDelayMs

    EgressOutboxProcessor(
            EgressOutboxRepository outboxRepository,
            EgressDecisionDeliveryService deliveryService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.outboxRepository = outboxRepository
        this.deliveryService = deliveryService
        this.objectMapper = objectMapper
        this.meterRegistry = meterRegistry
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void process(Long outboxId) {
        def optional = outboxRepository.findById(outboxId)
        if (!optional.present) return

        def row = optional.get()
        if (row.status != EgressOutboxStatus.PROCESSING) return

        try {
            DecisionEgressPayload payload = objectMapper.readValue(row.payloadJson, DecisionEgressPayload)
            deliveryService.deliver(payload)

            row.status = EgressOutboxStatus.SENT
            row.processedAt = Instant.now()
            row.lastError = null

            meterRegistry.counter("egress.outbox.dispatch.success.count").increment()
        } catch (Exception ex) {
            row.lastError = truncate(ex.message ?: ex.class.simpleName, 1000)

            if (row.attemptCount >= maxAttempts) {
                row.status = EgressOutboxStatus.DEAD
                row.nextAttemptAt = Instant.now()
                meterRegistry.counter("egress.outbox.dispatch.dead.count").increment()
                log.error("EGRESS-OUTBOX | DEAD | id={} | attempts={} | error={}", row.id, row.attemptCount, row.lastError)
            } else {
                row.status = EgressOutboxStatus.RETRY
                row.nextAttemptAt = Instant.now().plusMillis(computeDelayMs(row.attemptCount))
                meterRegistry.counter("egress.outbox.dispatch.retry.count").increment()
                log.warn("EGRESS-OUTBOX | RETRY | id={} | attempts={} | nextAttemptAt={} | error={}",
                        row.id, row.attemptCount, row.nextAttemptAt, row.lastError)
            }
        }
    }

    private long computeDelayMs(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 8))
        return baseDelayMs * (1L << exponent)
    }

    private static String truncate(String value, int max) {
        if (value == null) return null
        return value.length() <= max ? value : value.substring(0, max)
    }
}