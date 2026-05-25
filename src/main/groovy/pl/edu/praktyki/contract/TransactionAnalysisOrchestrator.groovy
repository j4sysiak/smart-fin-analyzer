package pl.edu.praktyki.contract

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyEntity
import pl.edu.praktyki.contract.idempotency.IdempotencyKeyRepository

@Service
class TransactionAnalysisOrchestrator {

    @PersistenceContext
    private EntityManager entityManager

    private final TransactionAnalyzer analyzer
    private final TransactionDecisionPolicy decisionPolicy
    private final IdempotencyKeyRepository idempotencyKeyRepository

    TransactionAnalysisOrchestrator(
            TransactionAnalyzer analyzer,
            TransactionDecisionPolicy decisionPolicy,
            IdempotencyKeyRepository idempotencyKeyRepository
    ) {
        this.analyzer = analyzer
        this.decisionPolicy = decisionPolicy
        this.idempotencyKeyRepository = idempotencyKeyRepository
    }

    TransactionDecision process(TransactionIngressRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null")
        }

        String correlationId = normalizeCorrelationId(request.correlationId)

        // Jeśli brak korelacji, działamy "legacy" (bez idempotencji)
        if (correlationId == null) {
            return computeDecision(request)
        }

        def existing = idempotencyKeyRepository.findByCorrelationId(correlationId)
        if (existing.present) {
            return toDecision(existing.get())
        }

        TransactionDecision computed = computeDecision(request)

        try {
            idempotencyKeyRepository.saveAndFlush(toEntity(correlationId, computed))
            return computed
        } catch (DataIntegrityViolationException ex) {
            // race condition: równoległy request zapisał ten sam correlationId chwilę wcześniej
            // Po nieudanym flush kontekst Hibernate może mieć niespójny stan encji — czyścimy go przed odczytem.
            entityManager.clear()
            def afterRace = idempotencyKeyRepository.findByCorrelationId(correlationId)
            if (afterRace.present) {
                return toDecision(afterRace.get())
            }
            throw ex
        }
    }

    private static String normalizeCorrelationId(String correlationId) {
        if (correlationId == null) return null
        String trimmed = correlationId.trim()
        return trimmed.isEmpty() ? null : trimmed
    }

    private TransactionDecision computeDecision(TransactionIngressRequest request) {
        AnalysisResult result = analyzer.analyze(request)
        return decisionPolicy.decide(request, result)
    }

    private static TransactionDecision toDecision(IdempotencyKeyEntity entity) {
        TransactionDecision.builder()
                .transactionId(entity.transactionId)
                .correlationId(entity.correlationId)
                .decision(entity.decision)
                .reason(entity.reason)
                .decidedAt(entity.decidedAt)
                .build()
    }

    private static IdempotencyKeyEntity toEntity(String correlationId, TransactionDecision decision) {
        new IdempotencyKeyEntity(
                correlationId: correlationId,
                transactionId: decision.transactionId,
                decision: decision.decision,
                reason: decision.reason,
                decidedAt: decision.decidedAt
        )
    }
}




























