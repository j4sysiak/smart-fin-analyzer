package pl.edu.praktyki.contract

import java.time.Instant

//@Service
class InMemoryTransactionAnalyzer implements TransactionAnalyzer {

    @Override
    AnalysisResult analyze(TransactionIngressRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null")
        }

        AnalysisStatus status = readStatusFromPayload(request.payload)

        return AnalysisResult.builder()
                .transactionId(request.transactionId)
                .correlationId(request.correlationId)
                .status(status)
                .analyzedAt(Instant.now())
                .details("In-memory analysis")
                .build()
    }

    private AnalysisStatus readStatusFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return AnalysisStatus.OK
        }

        def raw = payload.get("analysisStatus")

        if (raw == null) {
            return AnalysisStatus.OK
        }

        if (raw instanceof AnalysisStatus) {
            return (AnalysisStatus) raw
        }

        if (raw instanceof String) {
            try {
                return AnalysisStatus.valueOf(((String) raw).trim().toUpperCase())
            } catch (IllegalArgumentException ignored) {
                return AnalysisStatus.ERROR
            }
        }

        return AnalysisStatus.ERROR
    }
}