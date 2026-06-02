package pl.edu.praktyki.support.mock

/**
 * Manual runner for local curl checks against a mocked external document API.
 */
// Ten runner jest prostym narzędziem do uruchamiania lokalnego serwera WireMock, który to serwer symuluje zewnętrzne API dokumentów.
// Jest to przydatne do ręcznego testowania interakcji z API dokumentów za pomocą narzędzi takich jak curl, Postman itp.
// Można go uruchomić lokalnie, a następnie wykonywać zapytania HTTP do zdefiniowanych endpointów,
// aby zweryfikować, że nasz system poprawnie komunikuje się z API dokumentów.
// przykładowo, po uruchomieniu tego runnera, możemy wykonać curl do endpointu /api/documents/INV-2026-05-001?includeMetadata=true
// i zobaczyć, jak mock serwer odpowiada na to zapytanie, zwłaszcza jeśli mamy zdefiniowane scenariusze w pliku document-scenarios.json,
// które symulują różne stany dokumentów (np. READY, NOT_FOUND, ERROR itp.).

// przykłady curla:
// curl.exe "http://localhost:8089/api/documents/INV-2026-05-001?includeMetadata=true"
// curl.exe "http://localhost:8089/api/documents/INV-404"

class DocumentApiMockServerRunner {

    static void main(String[] args) {
        int port = Integer.parseInt(System.getProperty("doc.mock.port", "8089"))
        String scenariosPath = System.getProperty(
                "doc.mock.scenarios",
                "src/test/resources/mock/document-scenarios.json"
        )
        // Tworzymy i konfigurujemy mock serwer na podstawie podanych parametrów
        def mockServer = DocumentApiMockServer.fixedPort(port)
        mockServer.start()
        int loadedScenarios = mockServer.stubFromJsonFile(new File(scenariosPath))
        // Dodajemy shutdown hook, aby poprawnie zatrzymać serwer po zakończeniu programu
        Runtime.runtime.addShutdownHook(new Thread({
            mockServer.stop()
        }))

        println "Document API mock server started on ${mockServer.baseUrl()}"
        println "Loaded scenarios: ${loadedScenarios} from ${new File(scenariosPath).absolutePath}"
        println "Try: curl.exe \"${mockServer.baseUrl()}/api/documents/INV-2026-05-001?includeMetadata=true\""
        println "Press Ctrl+C to stop."

        while (true) {
            Thread.sleep(1000)
        }
    }
}

