package pl.edu.praktyki.support.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import static com.github.tomakehurst.wiremock.client.WireMock.*

/**
 * Reusable WireMock wrapper for external document provider API.
 */

// Ten mock serwer jest prostym wrapperem nad WireMock,
// który pozwala łatwo definiować stany i zachowania zewnętrznego API dokumentów.
// Można go używać w testach integracyjnych, aby symulować różne scenariusze odpowiedzi z API dokumentów:
//  - dokument gotowy
//  - dokument nie znaleziony
//  - błąd serwera itp.
// bez konieczności uruchamiania prawdziwego serwera dokumentów.
// Dzięki temu testy są szybsze, bardziej stabilne i łatwiejsze do debugowania.
// czyli  to po prostu symulator zewnętrznego API dokumentów, który możemy kontrolować w testach.
class DocumentApiMockServer {

    // WireMockServer jest thread-safe, więc możemy go bezpiecznie używać w wielu testach równocześnie.
    // Dzięki temu możemy mieć wiele instancji DocumentApiMockServer w różnych testach, każda z własną konfiguracją i stanem.
    // Każda instancja DocumentApiMockServer zarządza własnym WireMockServer, który jest uruchamiany na dynamicznym lub stałym porcie.
    // Dzięki temu możemy mieć wiele niezależnych mock serwerów w różnych testach, bez konfliktów portów.
    private final WireMockServer server

    private DocumentApiMockServer(WireMockServer server) {
        this.server = server
    }

    static DocumentApiMockServer dynamicPort() {
        return new DocumentApiMockServer(new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()))
    }

    static DocumentApiMockServer fixedPort(int port) {
        return new DocumentApiMockServer(new WireMockServer(WireMockConfiguration.wireMockConfig().port(port)))
    }

    void start() {
        server.start()
    }

    void stop() {
        if (server.isRunning()) {
            server.stop()
        }
    }

    void reset() {
        server.resetAll()
    }

    String baseUrl() {
        return server.baseUrl()
    }

    int port() {
        return server.port()
    }

    /**
     * Loads scenario definitions from JSON file and creates all stubs in one shot.
     * Expected format:
     * { "scenarios": [ { "id": "INV-1", "statusCode": 200, "includeMetadata": true, "body": {...} } ] }
     */

    // Ta metoda pozwala na zdefiniowanie wielu scenariuszy odpowiedzi z API dokumentów za pomocą jednego pliku JSON.
    // Plik JSON powinien zawierać tablicę "scenarios", gdzie każdy element definiuje scenariusz dla konkretnego ID dokumentu.
    // Każdy scenariusz może zawierać:
    // - "id": ID dokumentu (wymagane)
    // - "statusCode": kod HTTP odpowiedzi (domyślnie 200)
    // - "includeMetadata": czy oczekiwać parametru includeMetadata=true w zapytaniu (domyślnie false)
    // - "body": obiekt JSON do zwrócenia jako odpowiedź (opcjonalnie)
    // - "bodyText": surowy tekst do zwrócenia jako odpowiedź (opcjonalnie, nadpisuje "body")

    // stub - to po prostu definicja, jak ma wyglądać odpowiedź dla danego ID dokumentu, w zależności od tego, czy includeMetadata jest true czy false.
    // Dzięki tej metodzie możemy łatwo zarządzać wieloma scenariuszami testowymi, definiując je w jednym miejscu (plik JSON) i automatycznie tworząc odpowiednie stuby w WireMock.
    int stubFromJsonFile(File jsonFile) {
        if (!jsonFile?.exists()) {
            throw new IllegalArgumentException("Scenarios file does not exist: ${jsonFile?.absolutePath}")
        }

        def parsed = new JsonSlurper().parse(jsonFile)
        // Sprawdzamy, czy wczytany JSON zawiera tablicę "scenarios". Jeśli nie, rzucamy wyjątek.
        def scenarios = parsed?.scenarios
        if (!(scenarios instanceof List)) {
            throw new IllegalArgumentException("Invalid scenarios JSON. Expected top-level 'scenarios' array.")
        }

        // Dla każdego scenariusza w tablicy "scenarios" wywołujemy metodę stubSingleScenario,
        // która tworzy odpowiedni stub w WireMock.
        // stub to: definicja, jak ma wyglądać odpowiedź dla danego ID dokumentu,
        // w zależności od tego, czy includeMetadata jest true czy false.
        scenarios.each { s ->
            stubSingleScenario(s as Map)
        }
        return scenarios.size()
    }

    // closure scenario = „zamień scenariusz na stub”
    // scenarios.each { scenario(it) } = „zrób to samo dla wszystkich scenariuszy”
    int registerScenarios(List scenarios) {

           // definiujmy Closure to pozwala na wywołanie stubSingleScenario dla każdego scenariusza w liście.
           def scenarioClosure = { s -> stubSingleScenario(s as Map) }

           // To jest wywołanie closure dla każdego scenariusza w liście, co tworzy odpowiednie `stuby` w WireMock.
           scenarios.each { scenarioClosure(it) }

        return scenarios.size()
    }

    /**
     * Punkt 8 z Readme:  C:\dev\smart-fin-analyzer\Lab100--Wiremock-i-Closure--Wykłady\Readme.md

     A: generator stubow z listy scenariuszy (np. z JSON),
     Zamiast ręcznie stubować dokumenty, robisz closure, która bierze jeden rekord scenariusza i ustawia WireMocka.

     B: reuzywalne closure dla OK/404/500/timeout,
     Możesz mieć np. closure do:
     - dokumentu poprawnego,  def stubOk = { ... }
     - dokumentu brakującego, def stubNotFound = { ... }
     - dokumentu z błędem 500, def stubError = { ... }
     - dokumentu z timeoutem. def stubTimeout = { ... }
      i wreszcie: rejestrowanie scenariusza, def registerScenario = { ... }

     C: specyficzne zachowania (includeMetadata i ID pasujace do wzorca VIP-*).
     Np.:
     - jeśli request ma parametr includeMetadata=true, zwróć pełny JSON,
     - jeśli nie ma parametru, zwróć okrojony JSON,
     - jeśli id pasuje do wzorca, odpowiedz inaczej
     */
    int registerPracticalScenarios(List<Map> scenarios) {
        if (scenarios == null) {
            throw new IllegalArgumentException("Scenarios list must not be null")
        }

        // to jest closure do generowania stubów 200 OK (reużywalny kawałek logiki do tworzenia stubów sukcesu).
        def stubOk = { String id, boolean includeMetadata, Map body, Integer fixedDelayMs ->
            Map normalizedBody = (body ?: [id: id, status: "READY"]) as Map
            Map vipAwareBody = id ==~ /^VIP-.+/ ? (normalizedBody + [segment: "VIP"]) : normalizedBody
            Map reducedBody = buildReducedBody(vipAwareBody, id)

            // Najpierw fallback bez wymaganego query param - zwraca skrocone body.
            def fallbackResponse = aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(JsonOutput.toJson(reducedBody))
            if (fixedDelayMs != null && fixedDelayMs > 0) {
                fallbackResponse = fallbackResponse.withFixedDelay(fixedDelayMs)
            }
            server.stubFor(get(urlPathEqualTo("/api/documents/${id}"))
                    .willReturn(fallbackResponse))

            // Gdy includeMetadata=true, zwracamy pelny JSON.
            if (includeMetadata) {
                def fullResponse = aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(JsonOutput.toJson(vipAwareBody))
                if (fixedDelayMs != null && fixedDelayMs > 0) {
                    fullResponse = fullResponse.withFixedDelay(fixedDelayMs)
                }

                server.stubFor(get(urlPathEqualTo("/api/documents/${id}"))
                        .withQueryParam("includeMetadata", equalTo("true"))
                        .willReturn(fullResponse))
            }
        }

        // to jest closure do generowania stubów 404 Not Found (reużywalny kawałek logiki do tworzenia stubów błędu 404.)
        def stubNotFound = { String id ->
            server.stubFor(get(urlPathEqualTo("/api/documents/${id}"))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("Content-Type", "application/json")
                            .withBody('{"error":"DOCUMENT_NOT_FOUND"}')))
        }

        // to jest closure do generowania stubów 500 Internal Server Error (reużywalny kawałek logiki do tworzenia stubów błędu 500.)
        def stubError = { String id, String message ->
            String errorMessage = message ?: "DOCUMENT_PROVIDER_ERROR"
            server.stubFor(get(urlPathEqualTo("/api/documents/${id}"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody(JsonOutput.toJson([error: errorMessage]))))
        }

        // to jest closure do generowania stubów z opóźnieniem (timeout) (reużywalny kawałek logiki do tworzenia stubów z opóźnieniem).
        def stubTimeout = { String id, int statusCode, Map body, int fixedDelayMs, boolean includeMetadata ->
            Map timeoutBody = (body ?: [id: id, status: "PENDING"]) as Map
            def response = aResponse()
                    .withStatus(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody(JsonOutput.toJson(timeoutBody))
                    .withFixedDelay(fixedDelayMs)

            def mapping = get(urlPathEqualTo("/api/documents/${id}"))
            if (includeMetadata) {
                mapping = mapping.withQueryParam("includeMetadata", equalTo("true"))
            }
            server.stubFor(mapping.willReturn(response))
        }

        // to jest closure do rejestrowania scenariusza
        // (przetwarzanie pojedynczego scenariusza z listy) (reużywalny kawałek logiki do rejestrowania scenariusza).
        def registerScenario = { Map s ->
            String id = s.id as String
            if (!id) {
                throw new IllegalArgumentException("Each scenario must contain non-empty 'id'.")
            }

            int statusCode = (s.statusCode ?: 200) as int
            boolean includeMetadata = (s.includeMetadata ?: false) as boolean
            Map body = (s.body ?: [:]) as Map
            Integer fixedDelayMs = s.fixedDelayMs != null ? (s.fixedDelayMs as int) : null

            if (fixedDelayMs != null && fixedDelayMs > 0) {
                stubTimeout(id, statusCode, body, fixedDelayMs, includeMetadata)
                return
            }

            // to jest główny punkt decyzyjny, który na podstawie statusCode decyduje, jaki stub utworzyć:
            // - jeśli statusCode == 200, tworzymy stub OK
            // - jeśli statusCode == 404, tworzymy stub Not Found
            // - jeśli statusCode == 500, tworzymy stub Error
            if (statusCode == 200) {
                stubOk(id, includeMetadata, body, fixedDelayMs)
            } else if (statusCode == 404) {
                stubNotFound(id)
            } else if (statusCode == 500) {
                stubError(id, s.errorMessage as String)
            } else {
                stubSingleScenario(s)
            }
        }

        scenarios.each(registerScenario)
        return scenarios.size()
    }

    /**
     * Wygodna metoda pod LAB101: laduje scenariusze z JSON i stosuje praktyczny dispatcher
     * (200/404/500/timeout + includeMetadata + wzorzec VIP-*).
     */
    int registerPracticalScenariosFromJsonFile(File jsonFile) {
        if (!jsonFile?.exists()) {
            throw new IllegalArgumentException("Scenarios file does not exist: ${jsonFile?.absolutePath}")
        }

        def parsed = new JsonSlurper().parse(jsonFile)
        def scenarios = parsed?.scenarios
        if (!(scenarios instanceof List)) {
            throw new IllegalArgumentException("Invalid scenarios JSON. Expected top-level 'scenarios' array.")
        }

        List<Map> mappedScenarios = scenarios.collect { it as Map }
        return registerPracticalScenarios(mappedScenarios)
    }

    // Ta metoda pozwala na zdefiniowanie pojedynczego scenariusza,
    // w którym dokument o danym ID istnieje i jest gotowy do pobrania,
    // a API zwraca 200 z odpowiednim JSON-em.
    void stubDocumentOk(String documentId, boolean includeMetadata) {
        // Tworzymy mapping dla GET /api/documents/{documentId}
        def mapping = get(urlPathEqualTo("/api/documents/${documentId}"))
        if (includeMetadata) {
            mapping = mapping.withQueryParam("includeMetadata", equalTo("true"))
        }

        // Na podstawie documentId tworzymy JSON, który będzie zwracany jako odpowiedź.
        // W tym przypadku zakładamy, że dokument jest:
        //  - gotowy (status: READY)
        //  - należy do użytkownika JAN_KOWALSKI
        //  - ma contentType application/pdf
        //  - zawiera URL do pobrania pliku PDF.

        // Ostatecznie rejestrujemy ten stub w WireMock,
        // który będzie obsługiwał zapytania zgodnie z tym scenariuszem
        // (i zwracał 200 z odpowiednim JSON-em dla GET /api/documents/{documentId}).
        server.stubFor(mapping.willReturn(okJson("""
            {
              "id": "${documentId}",
              "status": "READY",
              "owner": "JAN_KOWALSKI",
              "contentType": "application/pdf",
              "downloadUrl": "https://documents.example.local/files/${documentId}.pdf"
            }
        """.stripIndent())))
    }

    // Ta metoda pozwala na zdefiniowanie scenariusza, w którym dokument o danym ID nie istnieje,
    // a API zwraca 404 z odpowiednim komunikatem błędu.
    void stubDocumentNotFound(String documentId) {
        // Ostatecznie rejestrujemy ten stub w WireMock, który będzie obsługiwał zapytania zgodnie z tym scenariuszem,
        // czyli zwracał 404 z JSON-em {"error":"DOCUMENT_NOT_FOUND"} dla GET /api/documents/{documentId}.
        server.stubFor(get(urlPathEqualTo("/api/documents/${documentId}"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody('{"error":"DOCUMENT_NOT_FOUND"}')))
    }

    void verifyDocumentRequested(String documentId, boolean includeMetadata) {
        def verification = getRequestedFor(urlPathEqualTo("/api/documents/${documentId}"))
        if (includeMetadata) {
            verification = verification.withQueryParam("includeMetadata", equalTo("true"))
        }
        // Ta metoda pozwala na weryfikację, że w trakcie testu został wykonany dokładnie jeden request do endpointu /api/documents/{id},
        // z opcjonalnym parametrem includeMetadata=true, w zależności od tego, jak został zdefiniowany scenariusz. Dzięki temu możemy sprawdzić,
        // czy nasz system poprawnie wywołał zewnętrzne API dokumentów zgodnie z oczekiwaniami testowymi.
        server.verify(1, verification)
    }

    // Ta metoda pozwala na zdefiniowanie pojedynczego scenariusza odpowiedzi dla konkretnego ID dokumentu,
    // na podstawie mapy z kluczami "id", "statusCode", "includeMetadata", "body" lub "bodyText".
    private void stubSingleScenario(Map scenario) {
        String id = scenario.id as String
        if (!id) {
            throw new IllegalArgumentException("Each scenario must contain non-empty 'id'.")
        }

        int statusCode = (scenario.statusCode ?: 200) as int
        boolean includeMetadata = scenario.includeMetadata as boolean
        String responseBody = toResponseBody(scenario)

        // Tworzymy mapping dla GET /api/documents/{id}, opcjonalnie z parametrem includeMetadata=true
        def requestBuilder = get(urlPathEqualTo("/api/documents/${id}"))
        if (includeMetadata) {
            requestBuilder = requestBuilder.withQueryParam("includeMetadata", equalTo("true"))
        }

        // Na podstawie statusCode i responseBody tworzymy odpowiedź, którą WireMock będzie zwracać dla tego scenariusza.
        // Dzięki temu możemy łatwo definiować różne scenariusze odpowiedzi (np. 200 z danymi, 404 z błędem, 500 z tekstem itp.)
        // w zależności od potrzeb testowych.

        // Ostatecznie rejestrujemy ten stub w WireMock, który będzie obsługiwał zapytania zgodnie z tym scenariuszem.
        // server.stubFor(...) zapisuje całość w serwerze WireMock.
        //    - aResponse() tworzy definicję odpowiedzi mocka.
        //    - .withStatus(statusCode) ustawia kod HTTP, np. 200, 404, 500.
        //    - .withHeader("Content\-Type", "application/json") ustawia nagłówek odpowiedzi.
        //    - .withBody(responseBody) ustawia treść odpowiedzi.
        //    - server.stubFor(...) zapisuje całość w serwerze WireMock.

        // W praktyce: Gdy test wyśle request pasujący do requestBuilder, mock server zwróci przygotowaną odpowiedź.
        // Czyli w praktyce to jest odpowiednik:
        //   - jeśli przyjdzie taki request,
        //   - to zwróć taki status, takie nagłówki i takie body.
        // To jest centralny moment, w którym scenariusz testowy staje się aktywny.
        // Stub  to testowa, uproszczona atrapa zależności, która zwraca wcześniej zdefiniowane odpowiedzi, aby kontrolować zachowanie testowanego kodu.
        // i tutaj właśnie tworzony jest `stub`, czyli definicja zachowania mocka dla określonego requestu.

/*


        ### Co jest w `document-scenarios.json`
        Jeśli w pliku jest `4` elementy w tablicy `scenarios`, to są to `4` scenariusze odpowiedzi.

                Każdy scenariusz mówi mniej więcej:

        - dla jakiego `id` dokumentu,
        - z jakim ewentualnym parametrem `includeMetadata`,
        - jaki ma być `statusCode`,
        - jakie ma być `body` albo `bodyText`.

        ### Co robi `stubSingleScenario(...)`
        Ta metoda bierze **jeden** scenariusz z JSON\-a i tworzy **jeden stub** w WireMock.

        Czyli:

        - `1` wpis w JSON \= `1` stub
        - `4` wpisy w JSON \= `4` stuby

        ### Co robi zaznaczony fragment
        Ten fragment:

        - buduje odpowiedź HTTP,
        - ustawia `statusCode`,
        - ustawia nagłówek `Content-Type: application/json`,
        - ustawia treść odpowiedzi `responseBody`,
        - rejestruje to w WireMock przez `server.stubFor(...)`

        Czyli: **ten kod zapisuje jeden konkretny przypadek zachowania mocka**.

        ### Czy stub ma 4 przypadki?
        Nie do końca.

                Dokładniej:

        - nie ma **jednego stuba z 4 przypadkami**,
                - są **4 osobne stuby**, jeśli w JSON są `4` scenariusze.

        ### Czy to jest „wpisane do stuba”?
        Tak.

                Każdy scenariusz z JSON zostaje zamieniony na definicję stuba w WireMock.

        Czyli można to rozumieć tak:

        - JSON opisuje przypadki,
        - `stubFromJsonFile(...)` je wczytuje,
                - `stubSingleScenario(...)` zamienia każdy przypadek na stub,
        - WireMock potem reaguje zgodnie z tymi stubami.
        */


        server.stubFor(requestBuilder.willReturn(
                aResponse()
                    .withStatus(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)))
    }

    private static String toResponseBody(Map scenario) {
        if (scenario.body != null) {
            return JsonOutput.toJson(scenario.body)
        }
        if (scenario.bodyText != null) {
            return scenario.bodyText as String
        }
        return '{}'
    }

    private static Map buildReducedBody(Map source, String id) {
        Map reduced = [
                id    : source.id ?: id,
                status: source.status ?: "READY"
        ]
        if (source.segment != null) {
            reduced.segment = source.segment
        }
        return reduced
    }
}

