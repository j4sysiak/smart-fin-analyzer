package pl.edu.praktyki.contract

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

class AmountThresholdAnalyzerSpec extends Specification {

    private AmountThresholdAnalyzer analyzer = new AmountThresholdAnalyzer(threshold: 10000)

    @Unroll
    def "kwota #amount powinna dac status #expectedStatus"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-100")
                .accountId("ACC-100")
                .correlationId("CORR-100")
                .timestamp(Instant.now())
                .amount(amount)
                .build()

        when:
        def result = analyzer.analyze(request)

        then:
        result.status == expectedStatus
        result.transactionId == request.transactionId
        result.correlationId == request.correlationId
        result.analyzedAt != null

        where:
        amount          || expectedStatus
        9999.99         || AnalysisStatus.OK
        10000.00        || AnalysisStatus.OK
        10000.01        || AnalysisStatus.FLAGGED
        99999.99        || AnalysisStatus.FLAGGED
    }

    def "brak kwoty (null) - powinien dac ERROR"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-101")
                .accountId("ACC-101")
                .correlationId("CORR-101")
                .timestamp(Instant.now())
                .amount(null)
                .build()

        when:
        def result = analyzer.analyze(request)

        then:
        result.status == AnalysisStatus.ERROR
    }
}