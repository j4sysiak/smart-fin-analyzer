package pl.edu.praktyki.contract

import org.springframework.stereotype.Service

import java.time.Instant

@Service
class DefaultTransactionDecisionPolicy implements TransactionDecisionPolicy {

    @Override
    TransactionDecision decide(TransactionIngressRequest request, AnalysisResult result) {
        String decision
        String reason

        switch (result.status) {
            case AnalysisStatus.OK:
                decision = "ACCEPT"
                reason = "Status OK - transaction accepted"
                break
            case AnalysisStatus.FLAGGED:
                decision = "ACCEPT_WITH_WARNING"
                reason = "Transaction flagged - manual review required"
                break
            case AnalysisStatus.TIMEOUT:
            case AnalysisStatus.ERROR:
            default:
                decision = "REJECT"
                reason = "Processing failed - transaction rejected"
                break
        }

        return TransactionDecision.builder()
                .transactionId(request.transactionId)
                .correlationId(request.correlationId)
                .decision(decision)
                .reason(reason)
                .decidedAt(Instant.now())
                .build()
    }
}