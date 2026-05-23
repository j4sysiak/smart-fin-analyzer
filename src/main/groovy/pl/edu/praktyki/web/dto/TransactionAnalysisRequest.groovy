package pl.edu.praktyki.web.dto

import groovy.transform.ToString
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

import java.time.Instant

@ToString(includeNames = true)
class TransactionAnalysisRequest {

    @NotBlank(message = "transactionId is required")
    String transactionId

    @NotBlank(message = "accountId is required")
    String accountId

    @NotBlank(message = "correlationId is required")
    String correlationId

    @NotNull(message = "timestamp is required")
    Instant timestamp

    // null -> w analyzerze obsłużysz jako ERROR/REJECT
    BigDecimal amount

    // dodatkowe dane wejściowe (opcjonalne)
    Map<String, Object> payload = [:]
}