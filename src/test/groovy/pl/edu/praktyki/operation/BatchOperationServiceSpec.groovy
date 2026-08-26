package pl.edu.praktyki.operation

import com.github.tomakehurst.wiremock.WireMockServer
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.BaseIntegrationSpec

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class BatchOperationServiceSpec extends BaseIntegrationSpec {

    @Autowired
    BatchOperationService batchOperationService

    @Autowired
    BankOperationClient bankOperationClient

    @Autowired
    OperationRepository operationRepository

    WireMockServer mockServer

    def setup() {
        mockServer = new WireMockServer(options().dynamicPort())
        mockServer.start()

        // Przekierowanie klienta na dynamiczny port WireMocka
        bankOperationClient.mockServerUrl = mockServer.baseUrl()

        // 1) deposits -> 2 rekordy
        mockServer.stubFor(get(urlEqualTo("/api/batch/deposits"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
[
  {"operationId":"OP-D-001","operationType":"DEPOSIT","targetAccount":"PL001","amount":100.00,"sourceCurrency":"PLN","correlationId":"BATCH-1"},
  {"operationId":"OP-D-002","operationType":"DEPOSIT","targetAccount":"PL002","amount":250.00,"sourceCurrency":"PLN","correlationId":"BATCH-1"}
]
""")))

        // 2) withdrawals -> 1 rekord
        mockServer.stubFor(get(urlEqualTo("/api/batch/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
[
  {"operationId":"OP-W-001","operationType":"WITHDRAWAL","sourceAccount":"PL003","amount":50.00,"sourceCurrency":"PLN","correlationId":"BATCH-1"}
]
""")))

        // 3) transfers -> 1 rekord
        mockServer.stubFor(get(urlEqualTo("/api/batch/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
[
  {"operationId":"OP-T-001","operationType":"TRANSFER","sourceAccount":"PL004","targetAccount":"PL005","amount":75.00,"sourceCurrency":"PLN","correlationId":"BATCH-1"}
]
""")))

        // 4) conversions -> 1 rekord
        mockServer.stubFor(get(urlEqualTo("/api/batch/conversions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
[
  {"operationId":"OP-C-001","operationType":"CONVERSION","sourceAccount":"PL006","amount":10.00,"sourceCurrency":"EUR","targetCurrency":"PLN","fxRate":4.25,"correlationId":"BATCH-1"}
]
""")))
    }

    def cleanup() {
        mockServer?.stop()
    }

    def "powinien pobrac operacje z mockservera i zapisac je do operations"() {
        when:
        def summary = batchOperationService.processAll()

        then:
        summary.total == 5
        summary.saved == 5
        summary.skipped == 0
        summary.failed == 0

        and:
        operationRepository.count() == 5
        operationRepository.findByOperationId("OP-D-001").present
        operationRepository.findByOperationId("OP-W-001").present
        operationRepository.findByOperationId("OP-T-001").present
        operationRepository.findByOperationId("OP-C-001").present
    }

    def "powinien pominac duplikaty po operationId"() {
        given:
        batchOperationService.processAll()

        when:
        def summary = batchOperationService.processAll()

        then:
        summary.total == 5
        summary.saved == 0
        summary.skipped == 5
        summary.failed == 0

        and:
        operationRepository.count() == 5
    }
}