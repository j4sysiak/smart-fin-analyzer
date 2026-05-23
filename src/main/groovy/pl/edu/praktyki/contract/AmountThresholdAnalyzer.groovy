package pl.edu.praktyki.contract

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

import java.time.Instant

@Service
class AmountThresholdAnalyzer implements TransactionAnalyzer {

    @Value('${banking.rules.amount-threshold:10000}')
    private BigDecimal threshold

    @Override
    AnalysisResult analyze(TransactionIngressRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null")
        }

        AnalysisStatus status = evaluateAmount(request.amount)

        return AnalysisResult.builder()
                .transactionId(request.transactionId)
                .correlationId(request.correlationId)
                .status(status)
                .analyzedAt(Instant.now())
                .details("Amount threshold check: threshold=${threshold}, amount=${request.amount}")
                .build()
    }

    private AnalysisStatus evaluateAmount(BigDecimal amount) {
        if (amount == null) {
            return AnalysisStatus.ERROR
        }
        return amount > threshold ? AnalysisStatus.FLAGGED : AnalysisStatus.OK
    }
}