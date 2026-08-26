package pl.edu.praktyki.operation

import groovy.transform.ToString
import java.time.OffsetDateTime

/**
 * Cel: oddzielić format wejściowy (JSON z MockServera) od encji DB.
 * DTO reprezentujące jedną operację finansową pobraną z MockServera.
 * Typy: DEPOSIT | WITHDRAWAL | TRANSFER | CONVERSION
 */
@ToString(includeNames = true)
class OperationDto {

    // Unikalny ID operacji z zewnętrznego systemu
    String operationId

    // Typ operacji: DEPOSIT | WITHDRAWAL | TRANSFER | CONVERSION
    String operationType

    // Konto źródłowe (wymagane dla WITHDRAWAL, TRANSFER, CONVERSION)
    String sourceAccount

    // Konto docelowe (wymagane dla TRANSFER)
    String targetAccount

    // Kwota operacji (zawsze > 0)
    BigDecimal amount

    // Waluta źródłowa
    String sourceCurrency

    // Waluta docelowa (wymagane tylko dla CONVERSION)
    String targetCurrency

    // Kurs wymiany (wymagane tylko dla CONVERSION)
    BigDecimal fxRate

    // ID paczki wsadowej — łączy operacje z tej samej sesji
    String correlationId

    // Surowy JSON z MockServera (do diagnostyki / audytu)
    String rawPayload

    OperationDto() {}

    // Wygodny konstruktor z Map (np. po parsowaniu JSON przez JsonSlurper)
    OperationDto(Map data) {
        this.operationId    = data.operationId as String
        this.operationType  = data.operationType as String
        this.sourceAccount  = data.sourceAccount as String
        this.targetAccount  = data.targetAccount as String
        this.amount         = data.amount as BigDecimal
        this.sourceCurrency = data.sourceCurrency as String
        this.targetCurrency = data.targetCurrency as String
        this.fxRate         = data.fxRate as BigDecimal
        this.correlationId  = data.correlationId as String
    }

    // Konwersja DTO -> Encja (zapis do bazy)
    OperationEntity toEntity() {
        def entity = new OperationEntity()
        entity.operationId    = this.operationId
        entity.operationType  = this.operationType
        entity.sourceAccount  = this.sourceAccount
        entity.targetAccount  = this.targetAccount
        entity.amount         = this.amount
        entity.sourceCurrency = this.sourceCurrency
        entity.targetCurrency = this.targetCurrency
        entity.fxRate         = this.fxRate
        entity.correlationId  = this.correlationId
        entity.payloadJson    = this.rawPayload
        entity.status         = "NEW"
        entity.occurredAt     = OffsetDateTime.now()
        return entity
    }
}