package pl.edu.praktyki.web.dto

import groovy.transform.ToString

import java.time.Instant

@ToString(includeNames = true)
class TransactionAnalysisResponse {

    String transactionId
    String correlationId

    // ACCEPT / ACCEPT_WITH_WARNING / REJECT
    String decision

    // np. "Status OK - transaction accepted"
    String reason

    Instant decidedAt
}