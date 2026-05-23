package pl.edu.praktyki.web

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pl.edu.praktyki.contract.TransactionAnalysisOrchestrator
import pl.edu.praktyki.contract.TransactionDecision
import pl.edu.praktyki.contract.TransactionIngressRequest
import pl.edu.praktyki.web.dto.TransactionAnalysisRequest
import pl.edu.praktyki.web.dto.TransactionAnalysisResponse

@RestController
@RequestMapping("/api/transactions")
class TransactionAnalysisController {

    private final TransactionAnalysisOrchestrator orchestrator

    TransactionAnalysisController(TransactionAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator
    }

    @PostMapping("/analyze")
    ResponseEntity<TransactionAnalysisResponse> analyze(@Valid @RequestBody TransactionAnalysisRequest request) {
        TransactionIngressRequest ingressRequest = toIngressRequest(request)
        TransactionDecision decision = orchestrator.process(ingressRequest)
        TransactionAnalysisResponse response = toResponse(decision)

        return ResponseEntity.ok(response)
    }

    private static TransactionIngressRequest toIngressRequest(TransactionAnalysisRequest request) {
        return TransactionIngressRequest.builder()
                .transactionId(request.transactionId)
                .accountId(request.accountId)
                .correlationId(request.correlationId)
                .timestamp(request.timestamp)
                .amount(request.amount)
                .payload(request.payload ?: [:])
                .build()
    }

    private static TransactionAnalysisResponse toResponse(TransactionDecision decision) {
        return new TransactionAnalysisResponse(
                transactionId: decision.transactionId,
                correlationId: decision.correlationId,
                decision: decision.decision,
                reason: decision.reason,
                decidedAt: decision.decidedAt
        )
    }
}