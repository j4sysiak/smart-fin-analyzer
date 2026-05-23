package pl.edu.praktyki.contract

import org.springframework.stereotype.Service
import org.springframework.cache.annotation.Cacheable

@Service
class TransactionAnalysisOrchestrator {

    private final TransactionAnalyzer analyzer
    private final TransactionDecisionPolicy decisionPolicy

    TransactionAnalysisOrchestrator(TransactionAnalyzer analyzer, TransactionDecisionPolicy decisionPolicy) {
        this.analyzer = analyzer
        this.decisionPolicy = decisionPolicy
    }

    @Cacheable(
            cacheNames = "transactionAnalysis",
            key = "#p0.correlationId",
            condition = "#p0 != null && #p0.correlationId != null && !#p0.correlationId.trim().isEmpty()"
    )
    TransactionDecision process(TransactionIngressRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null")
        }

        AnalysisResult result = analyzer.analyze(request)
        return decisionPolicy.decide(request, result)
    }
}