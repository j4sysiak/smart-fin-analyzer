package pl.edu.praktyki.contract

import groovy.transform.ToString
import groovy.transform.builder.Builder
import java.time.Instant

@Builder
@ToString(includeNames = true)
class TransactionIngressRequest {

    // unikalny identyfikator transakcji (np. "TX-20260522-001")
    String transactionId

    // identyfikator konta bankowego, z którego pochodzi transakcja
    String accountId

    // identyfikator korelacji requestu,
    // do śledzenia jednego requestu przez cały łańcuch pipeline'u
    String correlationId

    // kiedy request został wysłany do pipeline'u
    Instant timestamp

    // surowe dane transakcji (kwota, waluta, kategoria, opis itp.)
    Map<String, Object> payload = [:]

    BigDecimal amount
}

