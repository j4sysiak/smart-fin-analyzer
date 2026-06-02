# Quick Reference — Co robi każda klasa/plik?

## 1️⃣ Klasy i ich rola

| Klasa/Plik                          | Rola                                                      | Analogia                   |
|-------------------------------------|-----------------------------------------------------------|----------------------------|
| `DocumentApiMockServer`             | **Kontroler WireMock** — konfiguruje fake serwer          | "Kierownik zarządzający fake hotelem"  |
| `DocumentApiMockServerRunner`       | **Launcher** — uruchamia WireMock lokalnie do curl testów | "Portier otwierający drzwi na noc"     |
| `DocumentProviderMockServerSpec`    | **Unit test** — testuje kontrakt API automatycznie        | "Inspektor którzy sprawdza pokoje"     |
| `document-scenarios.json`           | **Seeded data** — lista dokumentów do stubowania         | "Rezerwacje gości w księdze"            |

---

## 2️⃣ Przepływ: JAK to działa?

```
┌──────────────────────────────────────────────────────────────────┐
│                         TWOJA APLIKACJA                          │
│                   (co chciała testować)                          │
└──────────────────────────────────────────────────────────────────┘
                            ↓
        "Chcę pobrać dokument z obcego API"
                            ↓
        externalDocumentService.getDocument("INV-001")
                            ↓
        Wysyła HTTP GET do http://localhost:8089/...
                            ↓
┌──────────────────────────────────────────────────────────────────┐
│                    WIREMOCK (FAKE SERVER)                        │
│                 (to co testujemy zamiast API)                   │
│                                                                   │
│  Otrzymuje request:                                              │
│  "GET /api/documents/INV-001"                                   │
│                                                                   │
│  WireMock szuka:                                                 │
│  "Czy mam stub dla tego URL? TAK!"                             │
│                                                                   │
│  Zwraca:                                                         │
│  200 OK + JSON dokument                                          │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
                            ↓
        Twoja aplikacja otrzyma JSON ✓
                            ↓
        Test sprawdzi: czy JSON jest OK?
                            ↓
        PASS ✅
```

---

## 3️⃣ Trzy sposoby użycia (i kiedy ich używać)

### A) TEST AUTOMATYCZNY (Spock)
```
Co?      Testowanie w CI/CD, szybki feedback, pull request
Kiedy?   Każdy commit, przed pushowaniem
Komenda: ./gradlew.bat documentMockTest
Efekt:   JUnit raport ✅ PASS lub ❌ FAIL
Czas:    <5 seconds
```

### B) MANUALNY TEST (curl)
```
Co?      Szybkie sprawdzenie API lokalnie
Kiedy?   Developer debuguje lokalnie
Komenda: ./gradlew.bat runDocumentMockServer
Następnie: curl.exe http://localhost:8089/...
Efekt:   Widzisz JSON w terminalu
Czas:    Interaktywne until Ctrl+C
```

### C) BATCH SCENARIUSZY (JSON)
```
Co?      Testowanie wielu dokumentów naraz
Kiedy?   Load testing, edge-case testing
Komenda: ./gradlew.bat documentMockTest
Dane:    Z document-scenarios.json wczytuje 3+ scenariusze
Efekt:   Testy dla: 200 OK, 404 NOT_FOUND, etc
Czas:    <5 seconds
```

---

## 4️⃣ Klasa po klasie — co się dzieje?

### DocumentApiMockServer — Centrala kontroli

```groovy
class DocumentApiMockServer {
  
  private WireMockServer server;  // ← RZECZYWISTY SERVER HTTP
  
  // Startuję serwer
  void start() {
    server.start()  // WireMock słucha na porcie (8089 lub custom)
  }
  
  // Czyszczę stuby z poprzedniego testu
  void reset() {
    server.resetAll()  // Usuń wszystkie definicje, serwer czeka na nowe
  }
  
  // Dodaję stub RĘCZNIE
  void stubDocumentOk(String docId, boolean withMetadata) {
    // "WireMock, gdy przyjdzie GET /api/documents/{docId}, 
    //  zwróć 200 + JSON dokument"
    server.stubFor(
      get(url...)
      .willReturn(200, json...)
    )
  }
  
  // NOWE: Czytam wiele stubów z JSON
  int stubFromJsonFile(File jsonFile) {
    // 1. Parsuj JSON: { "scenarios": [
    //      { "id": "INV-1", "statusCode": 200, "body": {...} },
    //      { "id": "INV-2", ...},
    //      { "id": "INV-404", "statusCode": 404, ...}
    //    ] }
    // 2. For each: dodaj stub do WireMock
    // 3. Return: liczba wczytanych
  }
  
  // Sprawdzam czy request poleciał
  void verify(String docId, boolean withMetadata) {
    server.verify(
      getRequestedFor(url...)  // "Czy WireMock dostał taki request?"
    )
  }
}
```

### DocumentApiMockServerRunner — Launcher dla curl

```groovy
class DocumentApiMockServerRunner {
  static void main(String[] args) {
    // 1. Wczytaj konfigurację
    int port = 8089  // lub -Ddoc.mock.port=8097
    String scenariosFile = "document-scenarios.json"
    
    // 2. Stwórz server na stałym porcie
    def mockServer = DocumentApiMockServer.fixedPort(port)
    mockServer.start()
    
    // 3. Załaduj scenariusze z JSON
    mockServer.stubFromJsonFile(new File(scenariosFile))
    
    // 4. Wypisz info
    println "Server started on http://localhost:${port}"
    println "Loaded 3 scenarios from document-scenarios.json"
    println "Try: curl http://localhost:${port}/api/documents/INV-001"
    
    // 5. Czekaj aż Ctrl+C
    while (true) {
      Thread.sleep(1000)
    }
  }
}
```

### DocumentProviderMockServerSpec — Unit testy (Spock)

```groovy
class DocumentProviderMockServerSpec extends Specification {
  
  @Shared
  DocumentApiMockServer documentApi = DocumentApiMockServer.dynamicPort()
  
  def setupSpec() {
    documentApi.start()
    // ✅ WireMock żyje przez cały czas testu
  }
  
  def cleanupSpec() {
    documentApi.stop()
    // ✅ WireMock mówi "do widzenia"
  }
  
  def setup() {
    documentApi.reset()
    // ✅ Czyszczę stuby przed każdym testem
  }
  
  def "test 1: pobranie dokumentu"() {
    given:
      documentApi.stubDocumentOk("INV-001", true)
      // WireMock: "Gotowa definicja dla INV-001"
    
    when:
      def response = httpClient.send(
        GET "http://localhost:8089/api/documents/INV-001?includeMetadata=true"
      )
      // ✅ Wysyłam PRAWDZIWY HTTP request do WireMock
    
    then:
      response.statusCode == 200
      response.body.id == "INV-001"
      // ✅ Sprawdzam odpowiedź
  }
  
  def "test 2: 404 dla brakującego dokumentu"() {
    given:
      documentApi.stubDocumentNotFound("INV-404")
    when:
      def response = httpClient.send(GET ".../INV-404")
    then:
      response.statusCode == 404
      response.body.error == "DOCUMENT_NOT_FOUND"
  }
  
  def "test 3: autogeneracja z JSON"() {
    given:
      documentApi.stubFromJsonFile(new File("document-scenarios.json"))
      // WireMock automatycznie dodaje wszystkie scenariusze z JSON
    when:
      // Testuję każdy scenariusz
      def r1 = request("INV-001")  // powinno być 200
      def r2 = request("INV-404")  // powinno być 404
    then:
      r1.statusCode == 200
      r2.statusCode == 404
  }
}
```

### document-scenarios.json — Dane testowe

```json
{
  "scenarios": [
    {
      "id": "INV-2026-05-001",
      "includeMetadata": true,
      "statusCode": 200,
      "body": {
        "id": "INV-2026-05-001",
        "status": "READY",
        "owner": "JAN_KOWALSKI",
        "contentType": "application/pdf"
      }
      // WireMock: Gdy przychodzi GET /api/documents/INV-2026-05-001?includeMetadata=true
      //           Zwróć: 200 OK + ten body
    },
    {
      "id": "INV-404",
      "statusCode": 404,
      "body": {
        "error": "DOCUMENT_NOT_FOUND"
      }
      // WireMock: Gdy przychodzi GET /api/documents/INV-404
      //           Zwróć: 404 NOT_FOUND + ten body (błąd)
    }
  ]
}
```

---

## 5️⃣ Step-by-step: Jak zacząć testować?

### Krok 1: Test automatyczny (Spock)
```bash
./gradlew.bat documentMockTest
```
✅ Wynik: 3 testy PASSED lub FAILED
⏱️ Czas: <5s
📊 Raport: JUnit XML + HTML

### Krok 2: Manualny test (curl)
```bash
./gradlew.bat runDocumentMockServer -PdocMockPort=8097
```
(W innym terminalu:)
```bash
curl.exe http://localhost:8097/api/documents/INV-2026-05-001?includeMetadata=true
```
✅ Wynik: JSON dokument lub error
⏱️ Czas: natychmiastowo
📊 Raport: cmdline output

### Krok 3: Dodaj własne scenariusze
Edytuj `document-scenarios.json`:
```json
{
  "scenarios": [
    // dodaj swoje dokumenty tutaj
  ]
}
```

---

## 6️⃣ Czym jest "stub"?

```
STUB = Definicja "jeśli-wtedy"

Przykład:
  JEŚLI: Otrzymam GET /api/documents/INV-001
  WTEDY: Zwróć 200 OK + JSON dokument

W WireMock:
  .stubFor(
    get(urlEqualTo("/api/documents/INV-001"))      // JEŚLI
    .willReturn(aResponse()
      .withStatus(200)
      .withBody(json))                              // WTEDY
  )
```

---

## 7️⃣ TL;DR — najważniejsze linijki kodu

```groovy
// ← START
mockServer = DocumentApiMockServer.dynamicPort()  // Stwórz server
mockServer.start()                                 // Uruchom

// ← KONFIGURACJA (wybierz jedną metodę)
mockServer.stubDocumentOk("INV-001", true)        // Ręczny stub
// ALBO
mockServer.stubFromJsonFile(jsonFile)             // Batch scenariuszy

// ← TEST
response = httpClient.send(GET /api/documents/INV-001)
assert response.statusCode == 200                 // Asercja

// ← WERYFIKACJA (opcjonalna)
mockServer.verify("GET", /api/documents/INV-001)  // Sprawdzenie kontrakt

// ← CLEANUP
mockServer.stop()                                  // Stop server
// ← KONIEC
```

---

## 8️⃣ Gdzie znaleźć więcej infirmacji?

📄 Schematy:
- `scripts/SCHEMAT--Testowanie-API-dokumentow-dengan-MockServer.md` ← Pełne wyjaśnienie
- `scripts/DIAGRAMY--Testowanie-API-wizualne.md` ← ASCII diagramy

💻 Kod:
- `src/test/groovy/pl/edu/praktyki/support/mock/DocumentApiMockServer.groovy`
- `src/test/groovy/pl/edu/praktyki/support/mock/DocumentApiMockServerRunner.groovy`
- `src/test/groovy/pl/edu/praktyki/integration/DocumentProviderMockServerSpec.groovy`
- `src/test/resources/mock/document-scenarios.json`

🔧 Gradle:
- `build.gradle` → taska `documentMockTest` i `runDocumentMockServer`

---

## ❓ FAQ

**P: Po co split na 3 klasy? Nie mogę zrobić jedną?**
A: Mogłeś, ale:
- `DocumentApiMockServer` = reuzywalny moduł (może go używać wiele testów)
- `DocumentApiMockServerRunner` = entry point dla manualnych testów (curl)
- `DocumentProviderMockServerSpec` = konkretne testy Spock dla dokumentów

**P: Czemu wczytować z JSON zamiast ręcznych stubów?**
A: Łatwiej skalować. Zamiast pisać:
```groovy
mockApi.stubDocumentOk("INV-001", true)
mockApi.stubDocumentOk("INV-002", true)
mockApi.stubDocumentOk("INV-003", true)
...
```
Robisz:
```groovy
mockApi.stubFromJsonFile("document-scenarios.json")
// Koniec. JSON zawiera 100 dokumentów? Wczytuje 100. Automatycznie.
```

**P: Czy WireMock może symulować timeout/500 error?**
A: Tak! W JSON dodaj:
```json
{ "id": "TIMEOUT", "statusCode": 500, "delayMs": 5000 }
```
(Jeszcze musisz to wdrożyć w DocumentApiMockServer, ale idea działa)

**P: Czemu "Mock" a nie "Stub"?**
A: 
- **Stub** = zwraca coś (dummy data)
- **Mock** = sprawdza czy się go wołało (verify)
WireMock robi oba, stąd "Mock"


