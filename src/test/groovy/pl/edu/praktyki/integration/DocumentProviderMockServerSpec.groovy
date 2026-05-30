package pl.edu.praktyki.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import static com.github.tomakehurst.wiremock.client.WireMock.*

class DocumentProviderMockServerSpec extends Specification {

    @Shared
    WireMockServer documentApi = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())

    @Shared
    HttpClient httpClient = HttpClient.newHttpClient()

    def setupSpec() {
        documentApi.start()
    }

    def cleanupSpec() {
        documentApi.stop()
    }

    def setup() {
        documentApi.resetAll()
    }

    def "powinien pobrac dokument JSON z zamockowanego systemu zewnetrznego"() {
        given: "mockowany serwer dokumentow wystawia endpoint GET /api/documents/{id}"
        def documentId = "INV-2026-05-001"

        documentApi.stubFor(get(urlPathEqualTo("/api/documents/${documentId}"))
                .withQueryParam("includeMetadata", equalTo("true"))
                .willReturn(okJson("""
                    {
                      "id": "${documentId}",
                      "status": "READY",
                      "owner": "JAN_KOWALSKI",
                      "contentType": "application/pdf",
                      "downloadUrl": "https://documents.example.local/files/${documentId}.pdf"
                    }
                """.stripIndent())))

        and: "nasz system wykonuje HTTP GET do zewnetrznego serwera"
        def request = HttpRequest.newBuilder()
                .uri(URI.create("${documentApi.baseUrl()}/api/documents/${documentId}?includeMetadata=true"))
                .GET()
                .build()

        when:
        def response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        def body = new JsonSlurper().parseText(response.body()) as Map

        then: "dostajemy poprawny JSON"
        response.statusCode() == 200
        body.id == documentId
        body.status == "READY"
        body.owner == "JAN_KOWALSKI"
        body.contentType == "application/pdf"

        and: "weryfikujemy kontrakt requestu do obcego systemu"
        documentApi.verify(1, getRequestedFor(urlPathEqualTo("/api/documents/${documentId}"))
                .withQueryParam("includeMetadata", equalTo("true")))
    }

    def "powinien zwrocic 404 gdy dokument nie istnieje"() {
        given:
        def missingId = "INV-404"

        documentApi.stubFor(get(urlPathEqualTo("/api/documents/${missingId}"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody('{"error":"DOCUMENT_NOT_FOUND"}')))

        def request = HttpRequest.newBuilder()
                .uri(URI.create("${documentApi.baseUrl()}/api/documents/${missingId}"))
                .GET()
                .build()

        when:
        def response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        def body = new JsonSlurper().parseText(response.body()) as Map

        then:
        response.statusCode() == 404
        body.error == "DOCUMENT_NOT_FOUND"
    }
}

