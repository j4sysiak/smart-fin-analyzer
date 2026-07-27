package pl.edu.praktyki.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import groovy.json.JsonSlurper
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

/**
 * Punkt 6 z Readme.md:  "Jak closure pomaga przy JSON scenariuszach?"
 *  C:\dev\smart-fin-analyzer\Lab100--Wiremock-i-Closure--Wykłady\Readme.md
 * Idea:
 *  1. Wczytujesz scenariusze z pliku JSON
 *  2. Definiujesz closure registerScenario — wie jak zamienić jeden scenariusz na WireMock stub
 *  3. Wywołujesz: scenarios.each(registerScenario)
 *  4. Test operuje tylko na danych biznesowych, nie na szczegółach WireMocka
 */
class ClosureWithJsonScenariosSpec extends Specification {

    WireMockServer mockServer

    def setup() {
        mockServer = new WireMockServer(options().dynamicPort())
        mockServer.start()
    }

    def cleanup() {
        mockServer?.stop()
    }

    // ==========================================================================
    // Scenariusz A — closure zdefiniowana w teście + dane z pliku JSON
    // Dokładnie ten wzorzec z README punkt 6:
    //
    //   def registerScenario = { Map s -> ... }
    //   scenarios.each(registerScenario)
    //
    // ==========================================================================
    def "punkt 6 - closure registerScenario rejestruje stuby z pliku JSON"() {
        // stub to jest "gdy przyjdzie taki request, zwróć taką odpowiedź"
        // to jest taki scenariusz: "gdy przyjdzie request o id=INV-2026-05-001 i includeMetadata=true, zwróć 200 READY"
        given:
        // === KROK 1: wczytaj scenariusze z pliku JSON ===
        // Plik: src/test/resources/mock/document-scenarios.json
        // Zawiera tablicę "scenarios" z mapami opisującymi każdy scenariusz.
        def scenariosFile = new File("src/test/resources/mock/document-scenarios.json")
        def parsed = new JsonSlurper().parse(scenariosFile)
        def scenarios = parsed.scenarios as List<Map>

        // === KROK 2: zdefiniuj zmienna registerScenario jako closure ===
        // To jest SERCE punktu 6 z README.
        // Closure registerScenario wie jak zamienić JEDEN scenariusz (Map) na stub WireMocka.
        // Nie interesuje go ile jest scenariuszy — bierze jeden i robi swoje potem następny i tak dalej.
        def registerScenario = { Map s ->
            def path = "/api/documents/${s.id}"
            def mapping = get(urlPathEqualTo(path))

            // jeśli scenariusz wymaga parametru includeMetadata=true, dodajemy go do requestu
            if (s.includeMetadata) {
                mapping = mapping.withQueryParam("includeMetadata", equalTo("true"))
            }

            // rejestrujemy stub: "gdy przyjdzie taki request, zwróć taką odpowiedź"
            mockServer.stubFor(
                mapping.willReturn(
                    aResponse()
                        .withStatus(s.statusCode as int)
                        .withHeader("Content-Type", "application/json")
                        .withBody(groovy.json.JsonOutput.toJson(s.body))
                )
            )
        }

        // === KROK 3: użyj tego closure na każdym scenariuszu ===
        // To jest magia Groovy:
        //   scenarios.each(registerScenario)
        // zamiast:
        //   for (s in scenarios) { registerScenario(s) }
        // Jest krótsze i bardziej "językowe" i takie groovy'owe. Closure jest wywoływana dla każdego elementu listy scenarios.
        scenarios.each(registerScenario)

        when:
        // odpytujemy WireMocka jak prawdziwe HTTP — żeby zobaczyć, że stuby działają
        def ready1 = fetch("${mockServer.baseUrl()}/api/documents/INV-2026-05-001?includeMetadata=true")
        def ready2 = fetch("${mockServer.baseUrl()}/api/documents/INV-2026-05-002")
        def missing = fetch("${mockServer.baseUrl()}/api/documents/INV-404")

        then:
        // scenariusz 1: dokument istnieje, includeMetadata=true → 200 READY
        ready1.status == 200
        ready1.body.status == "READY"
        ready1.body.owner == "JAN_KOWALSKI"

        // scenariusz 2: dokument istnieje, bez includeMetadata → 200 READY
        ready2.status == 200
        ready2.body.status == "READY"
        ready2.body.owner == "JACEK_NOWAK"

        // scenariusz 3: dokument nie istnieje → 404
        missing.status == 404
        missing.body.error == "DOCUMENT_NOT_FOUND"
    }

    // ==========================================================================
    // Scenariusz B — ta sama closure, ale dane z listy map (bez pliku JSON)
    // Pokazuje, że closure jest niezależna od źródła danych.
    // Możesz ją użyć z danymi z JSON, z bazy, z kodu — nie ma różnicy.
    // ==========================================================================
    def "punkt 6 - closure registerScenario działa też z listą map w teście"() {
        given:
        // === dane zdefiniowane inline, bez pliku JSON ===
        def scenarios = [
            [id: "DOC-200", statusCode: 200, includeMetadata: false,
             body: [id: "DOC-200", status: "READY", owner: "ADMIN"]],
            [id: "DOC-404", statusCode: 404, includeMetadata: false,
             body: [error: "DOCUMENT_NOT_FOUND"]],
            [id: "DOC-500", statusCode: 500, includeMetadata: false,
             body: [error: "INTERNAL_SERVER_ERROR"]]
        ]

        // === closure — identyczna jak wyżej ===
        // Nie wie skąd przyszły dane. Po prostu bierze Map i robi stub.
        def registerScenario = { Map s ->
            def mapping = get(urlPathEqualTo("/api/documents/${s.id}"))
            if (s.includeMetadata) {
                mapping = mapping.withQueryParam("includeMetadata", equalTo("true"))
            }
            mockServer.stubFor(
                mapping.willReturn(
                    aResponse()
                        .withStatus(s.statusCode as int)
                        .withHeader("Content-Type", "application/json")
                        .withBody(groovy.json.JsonOutput.toJson(s.body))
                )
            )
        }

        // === jeden call, wszystkie stuby zarejestrowane ===
        scenarios.each(registerScenario)

        when:
        def ok = fetch("${mockServer.baseUrl()}/api/documents/DOC-200")
        def notFound = fetch("${mockServer.baseUrl()}/api/documents/DOC-404")
        def error = fetch("${mockServer.baseUrl()}/api/documents/DOC-500")

        then:
        ok.status == 200
        ok.body.owner == "ADMIN"

        notFound.status == 404
        notFound.body.error == "DOCUMENT_NOT_FOUND"

        error.status == 500
        error.body.error == "INTERNAL_SERVER_ERROR"
    }

    // ==========================================================================
    // Pomocnicza metoda do wywołania HTTP — identyczna jak w DocumentApiMockServerSpec
    // ==========================================================================
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

