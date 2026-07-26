package pl.edu.praktyki.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import groovy.json.JsonSlurper
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class DocumentApiMockServerSpec extends Specification {

    WireMockServer mockServer
    pl.edu.praktyki.wiremock.DocumentApiMockServer documentApiMockServer

    def setup() {
        mockServer = new WireMockServer(options().dynamicPort())
        mockServer.start()
        documentApiMockServer = new pl.edu.praktyki.wiremock.DocumentApiMockServer(mockServer: mockServer)
    }

    def cleanup() {
        mockServer?.stop()
    }

    def "powinien zwrócić READY dla INV-001 oraz 404 dla INV-404"() {
        given:
        // Rejestrujemy scenariusze w mockowanym serwerze dokumentów
        // Rejestrujemy odpowiedzi dla istniejącego dokumentu oraz przypadku 404.
        documentApiMockServer.registerBasicScenarios()

        when:
        def ready = fetch("${mockServer.baseUrl()}/api/documents/INV-001")
        def missing = fetch("${mockServer.baseUrl()}/api/documents/INV-404")

        then:
        ready.status == 200
        ready.body.status == "READY"
        ready.body.currency == "PLN"

        missing.status == 404
        missing.body.error == "DOCUMENT_NOT_FOUND"
    }

    private static Map fetch(String url) {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection()
        conn.requestMethod = "GET"
        int status = conn.responseCode

        InputStream stream = status >= 400 ? conn.errorStream : conn.inputStream
        String text = stream?.getText("UTF-8")
        Map body = text ? (Map) new JsonSlurper().parseText(text) : [:]

        [status: status, body: body]
    }
}