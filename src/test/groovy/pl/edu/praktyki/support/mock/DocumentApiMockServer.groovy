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
        scenarios.each { Map scenario ->
            stubSingleScenario(scenario)
        }
        return scenarios.size()
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
        // który będzie obsługiwał zapytania zgodnie z tym scenariuszem (i zwracał 200 z odpowiednim JSON-em dla GET /api/documents/{documentId}).
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
        server.stubFor(requestBuilder.willReturn(aResponse()
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
}

