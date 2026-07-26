package pl.edu.praktyki.support.mock

import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Ten test integracyjny pokazuje, jak używać DocumentApiMockServer do testowania interakcji
// z zewnętrznym systemem dokumentów.
// Jest to przykład testu integracyjnego, który może być uruchamiany lokalnie lub w pipeline CI,
// aby zweryfikować, że nasz system poprawnie komunikuje się z API dokumentów.

class DocumentProviderMockServerSpec extends Specification {

    private static final File SCENARIOS_FILE = new File("src/test/resources/mock/document-scenarios.json")

    @Shared
    DocumentApiMockServer documentApi = DocumentApiMockServer.dynamicPort()

    @Shared
    HttpClient httpClient = HttpClient.newHttpClient()

    def setupSpec() {
        documentApi.start()
    }

    def cleanupSpec() {
        documentApi.stop()
    }

    def setup() {
        documentApi.reset()
    }

    def "powinien pobrać dokument JSON z zamocowanego systemu zewnętrznego"() {
        given: "mockowany serwer dokumentów wystawia endpoint GET /api/documents/{id}"
        def documentId = "INV-2026-05-001"
        // Przygotowujemy stub, który symuluje odpowiedź z zewnętrznego API dokumentów dla danego ID.
        // documentApi to nasz mock serwer (http://localhost:55159), który pozwala nam definiować,
        // jakie odpowiedzi ma zwracać na konkretne zapytania.
        documentApi.stubDocumentOk(documentId, true)

        and: "nasz system wykonuje HTTP GET do zewnętrznego serwera"
        def request = HttpRequest.newBuilder()
                .uri(URI.create("${documentApi.baseUrl()}/api/documents/${documentId}?includeMetadata=true"))  // http://localhost:55159/api/documents/INV-2026-05-001?includeMetadata=true
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
        documentApi.verifyDocumentRequested(documentId, true)
    }

    def "powinien zwrócić 404 gdy dokument nie istnieje"() {
        given:
        def missingId = "INV-404"
        documentApi.stubDocumentNotFound(missingId)

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

    def "powinien automatycznie wygenerować wiele stubów z pliku json"() {
        given:
        // Ten test pokazuje, jak można zdefiniować wiele scenariuszy odpowiedzi w jednym pliku JSON (src/test/resources/mock/document-scenarios.json),
        // a następnie załadować je do naszego mock serwera za pomocą metody stubFromJsonFile.
        // Plik JSON zawiera listę scenariuszy, z których każdy definiuje, jak ma wyglądać odpowiedź dla konkretnego ID dokumentu.
        // Dzięki temu możemy łatwo zarządzać wieloma scenariuszami testowymi w jednym miejscu,
        // bez konieczności ręcznego definiowania stubów w kodzie testu.
        // Stub to testowa, uproszczona atrapa zależności, która zwraca wcześniej zdefiniowane odpowiedzi, aby kontrolować zachowanie testowanego kodu.
        // W tym teście sprawdzamy zarówno scenariusz, w którym dokument istnieje (ID: INV-2026-05-001, INV-2026-05-002),
        // jak i scenariusz, w którym dokument nie istnieje (ID: INV-404).
        int loaded = documentApi.stubFromJsonFile(SCENARIOS_FILE)

        when:
        def request200 = HttpRequest.newBuilder()
                .uri(URI.create("${documentApi.baseUrl()}/api/documents/INV-2026-05-001?includeMetadata=true"))
                .GET()
                .build()
        // response200 to odpowiedź z mockowanego serwera dla dokumentu wg scenariusza w Stubie dla id = INV-2026-05-001.
        def response200 = httpClient.send(
                request200,
                HttpResponse.BodyHandlers.ofString()
        )

        def request404 = HttpRequest.newBuilder()
                .uri(URI.create("${documentApi.baseUrl()}/api/documents/INV-404"))
                .GET()
                .build()
        // respose404 to odpowiedź z mockowanego serwera dla dokumentu wg scenariusza w Stubie dla id = INV-404.
        def response404 = httpClient.send(
                request404,
                HttpResponse.BodyHandlers.ofString()
        )

        def body200 = new JsonSlurper().parseText(response200.body()) as Map
        def body404 = new JsonSlurper().parseText(response404.body()) as Map

        then:
        loaded >= 3
        response200.statusCode() == 200
        body200.id == "INV-2026-05-001"
        body200.owner == "JAN_KOWALSKI"

        and:
        response404.statusCode() == 404
        body404.error == "DOCUMENT_NOT_FOUND"
    }




    // tworzy listę map scenarios
    // woła documentApi.registerScenarios(scenarios)
    // sprawdza odpowiedź 200 i 404

    // Co to znaczy praktycznie
    // To jest właśnie ten „mini-DSL” z README: punkt-4  (C:\dev\smart-fin-analyzer\Lab100--Wiremock-i-Closure--Wykłady\Readme.md)
    // każda mapa w scenarios = jeden scenariusz
    // closure scenario = „zamień scenariusz na stub”
    // scenarios.each { scenario(it) } = „zrób to samo dla wszystkich scenariuszy”

    // Czyli zamiast:
    //  - stubDocumentOk(...)
    //  - stubDocumentNotFound(...)
    //  - stubDocumentError(...)
    // mamy jedną logiczną ścieżkę:
    // scenarios.each { scenario(it) }

    def "powinien zarejestrować scenariusze przez closure mini-DSL"() {

        given:
        def scenarios = [
                [id: "INV-DSL-001", statusCode: 200, includeMetadata: true, body: [id: "INV-DSL-001", status: "READY", owner: "JAN_KOWALSKI"]],
                [id: "INV-DSL-404", statusCode: 404, body: [error: "DOCUMENT_NOT_FOUND"]]
        ]

        def registered = documentApi.registerScenarios(scenarios)

        when:
        def okRequest = HttpRequest.newBuilder()
                .uri(URI.create("${documentApi.baseUrl()}/api/documents/INV-DSL-001?includeMetadata=true"))
                .GET()
                .build()

        def missingRequest = HttpRequest.newBuilder()
                .uri(URI.create("${documentApi.baseUrl()}/api/documents/INV-DSL-404"))
                .GET()
                .build()

        def okResponse = httpClient.send(okRequest, HttpResponse.BodyHandlers.ofString())
        def missingResponse = httpClient.send(missingRequest, HttpResponse.BodyHandlers.ofString())

        def okBody = new JsonSlurper().parseText(okResponse.body()) as Map
        def missingBody = new JsonSlurper().parseText(missingResponse.body()) as Map

        then:
        registered == 2
        okResponse.statusCode() == 200
        okBody.id == "INV-DSL-001"
        okBody.status == "READY"
        okBody.owner == "JAN_KOWALSKI"

        missingResponse.statusCode() == 404
        missingBody.error == "DOCUMENT_NOT_FOUND"
    }
}
