package pl.edu.praktyki.contract

import groovy.transform.ToString
import groovy.transform.builder.Builder
import java.time.Instant

@Builder
@ToString(includeNames = true)
class TransactionDecision {

    // identyfikator transakcji (ten sam co w TransactionIngressRequest / AnalysisResult)
    String transactionId

    // identyfikator korelacji (ten sam przez cały łańcuch)
    String correlationId

    // decyzja: ACCEPT / ACCEPT_WITH_WARNING / REJECT
    String decision

    // uzasadnienie decyzji (np. "Status OK - transaction accepted" / "Fraud suspected - rejected")
    String reason

    // kiedy decyzja została podjęta
    Instant decidedAt
}

