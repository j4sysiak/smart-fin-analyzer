package pl.edu.praktyki.contract

import groovy.transform.ToString
import groovy.transform.builder.Builder
import java.time.Instant

@Builder
@ToString(includeNames = true)
class AnalysisResult {

    // identyfikator transakcji (ten sam co w TransactionIngressRequest)
    String transactionId

    // identyfikator korelacji (ten sam co w TransactionIngressRequest)
    String correlationId

    // wynik analizy reguł / wykrywania fraudu
    AnalysisStatus status

    // kiedy analiza została zakończona
    Instant analyzedAt

    // opcjonalny opis wyniku (np. "All rules passed" / "High amount at night — fraud suspected")
    String details
}

