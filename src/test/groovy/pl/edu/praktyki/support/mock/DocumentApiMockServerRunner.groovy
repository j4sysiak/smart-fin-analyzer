package pl.edu.praktyki.support.mock

/**
 * Manual runner for local curl checks against a mocked external document API.
 */
// Ten runner uruchamia lokalny serwer WireMock, który symuluje zewnętrzne API dokumentów.
// Służy do ręcznego testowania integracji, np. za pomocą curl lub Postman.
// Po uruchomieniu można wysyłać żądania HTTP do zdefiniowanych endpointów
// i weryfikować, czy aplikacja poprawnie komunikuje się z API dokumentów.
// Na przykład można wykonać żądanie do endpointu
// /api/documents/INV-2026-05-001?includeMetadata=true
// i sprawdzić odpowiedź mock serwera na podstawie scenariuszy z pliku
// document-scenarios.json, które symulują różne stany dokumentu
// \(np. READY, NOT_FOUND, ERROR\).

// Przykładowe polecenia curl:
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
        // to jest przykładowa odpowiedź, którą można uzyskać z mock serwera dla dokumentu INV-2026-05-001
        // po wpisaniu do przeglądarki zawartości tego curl (lub po prosu curla w terminalu).:
        // Przykładowe polecenia curl:
        // curl.exe "http://localhost:8089/api/documents/INV-2026-05-001?includeMetadata=true"
        // curl.exe "http://localhost:8089/api/documents/INV-404"

        println "Example response:"
        println """ {
            "id": "INV-2026-05-001",
            "status": "READY",
            "owner": "JAN_KOWALSKI",
            "contentType": "application/pdf",
            "downloadUrl": "https://documents.example.local/files/INV-2026-05-001.pdf"  
        } """.stripIndent()

        println "Press Ctrl+C to stop."

        while (true) {
            Thread.sleep(1000)
        }
    }
}



