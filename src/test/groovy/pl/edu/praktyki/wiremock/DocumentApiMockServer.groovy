package pl.edu.praktyki.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import groovy.json.JsonOutput

import static com.github.tomakehurst.wiremock.client.WireMock.*

class DocumentApiMockServer {

    WireMockServer mockServer

    void registerBasicScenarios() {
        stubDocument("INV-001", 200, [
                id      : "INV-001",
                status  : "READY",
                amount  : 1250.50,
                currency: "PLN"
        ])

        stubDocument("INV-002", 200, [
                id      : "INV-002",
                status  : "PROCESSING",
                amount  : 999.99,
                currency: "EUR"
        ])

        stubDocument("INV-404", 404, [
                error  : "DOCUMENT_NOT_FOUND",
                message: "Document INV-404 does not exist"
        ])
    }

    // Closure ukrywający konfigurację WireMocka
    def stubDocument = { String id, int status, Map body ->
        mockServer.stubFor(
                get(urlPathEqualTo("/api/documents/${id}"))
                        .willReturn(
                                aResponse()
                                        .withStatus(status)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(JsonOutput.toJson(body))
                        )
        )
    }

}