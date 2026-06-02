# Wizualna mapa testowania API dokumentów

## DIAGRAM 1: Architektura — Gdzie żyje WireMock?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          TWÓJ KOMPUTER (Developer)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌─────────────────────────────────┐      ┌──────────────────────────────┐   │
│  │   1. TWÓJ SYSTEM                │      │   2. WireMock FAKE API       │   │
│  │   (SmartFinAnalyzer)            │      │   (localhost:8089)           │   │
│  │                                 │      │                              │   │
│  │  HttpClient.send(               │      │  ┌─────────────────────────┐ │   │
│  │   GET /api/documents/INV-001    │──────→  │ Stub = definicja:       │ │   │
│  │   ?includeMetadata=true)        │      │  │ "GET /api/documents/INV │ │   │
│  │                                 │      │  │ Zwróć: 200 + JSON"      │ │   │
│  │                                 │      │  │                         │ │   │
│  │  ← Oczekuję: 200 OK + JSON      │←─────   │ Zwraca JSON:              │ │   │
│  │                                 │      │  │ { id: INV-001, ... }    │ │   │
│  │                                 │      │  │                         │ │   │
│  │  Sprawdzam asercje:             │      │  │ Loguje request:         │ │   │
│  │  • Status = 200 ✓               │      │  │ "GET /api/documents/..." │ │   │
│  │  • body.id = "INV-001" ✓        │      │  │ (do verify)              │ │   │
│  │  • body.owner ❌                │      │  └─────────────────────────┘ │   │
│  │                                 │      │  [FAKE Server - pod kontrolą]│   │
│  └─────────────────────────────────┘      └──────────────────────────────┘   │
│                                                                               │
│           ↑ Myśli: Rozmawiam z prawdziwym API                                │
│           ↓ Faktycznie: Rozmawiam z WireMock (moja kontrola!)                │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │  3. PLIK JSON: document-scenarios.json (dane testowe)                   │ │
│  │                                                                         │ │
│  │  {                                                                      │ │
│  │    "scenarios": [                                                      │ │
│  │      { "id": "INV-001", "statusCode": 200, "body": {...} },           │ │
│  │      { "id": "INV-404", "statusCode": 404, "body": {...} },           │ │
│  │      { "id": "TIMEOUT", "statusCode": 500, "body": {...} }            │ │
│  │    ]                                                                    │ │
│  │  }                                                                      │ │
│  │                                                                         │ │
│  │  (WireMock czyta ten plik → automatycznie tworzy stuby)               │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## DIAGRAM 2: Przepływ testu Spock (czego się dzieje krok po kroku)

```
URUCHAMIAM: ./gradlew.bat documentMockTest
                            ↓

        ┌───────────────────────────────────────┐
        │  setupSpec() - RUN ONCE (once per spec)│
        └───────────────────────────────────────┘
                            ↓
        documentApi = DocumentApiMockServer.dynamicPort()
                            ↓
        documentApi.start()
                            ↓
        ✅ WireMock serwer LIVE na porcie 8089
                            ↓

        ┌───────────────────────────────────────┐
        │  Pętla testów (każdy test = reset)     │
        └───────────────────────────────────────┘
                            ↓
        
        ╔═══════ TEST 1: "powinien pobrać dokument" ═════════╗
        ║                       
        ║  setup() ← Czyszczę WireMock z poprzednich stubów
        ║  │
        ║  given: "mockowany serwer wystawia endpoint..."
        ║         mockApi.stubDocumentOk("INV-001", true)
        ║         └─→ WireMock: "Dodaję stub"
        ║            GET /api/documents/INV-001?includeMetadata=true
        ║            ↓ (gdy przychodzi)
        ║            200 OK + JSON dokument
        ║
        ║  when: "nasz system wykonuje HTTP GET..."
        ║         response = httpClient.send(request)
        ║         └─→ Request leci do localhost:8089
        ║            └→ WireMock przechwytuje
        ║               └→ Szuka stubu dla GET /api/documents/INV-001
        ║                  └→ ZNALAZŁ! Zwraca 200 + JSON
        ║
        ║  then: "dostajemy poprawny JSON"
        ║         assert response.statusCode == 200 ✓
        ║         assert body.id == "INV-001" ✓
        ║         assert body.owner == "JAN_KOWALSKI" ✓
        ║         mockApi.verify(1, getRequestedFor(...)) ✓
        ║         └─→ "Czy WireMock faktycznie dostał taki request?"
        ║
        ║  RESULT: PASSED ✅
        ╚═════════════════════════════════════════════════════╝
                            ↓

        ╔═══════ TEST 2: "powinien zwrócić 404..." ═════════╗
        ║                       
        ║  setup()
        ║  given: mockApi.stubDocumentNotFound("INV-404")
        ║         └─→ WireMock: GET /api/documents/INV-404 → 404
        ║
        ║  when: response = httpClient.send(GET /api/documents/INV-404)
        ║         └─→ WireMock zwraca 404
        ║
        ║  then: assert response.statusCode == 404 ✓
        ║         assert body.error == "DOCUMENT_NOT_FOUND" ✓
        ║
        ║  RESULT: PASSED ✅
        ╚═════════════════════════════════════════════════════╝
                            ↓

        ╔═══════ TEST 3: "autogeneracja JSON scenariuszy" ═════════╗
        ║
        ║  setup()
        ║  given: mockApi.stubFromJsonFile(document-scenarios.json)
        ║         └─→ WireMock czyta JSON
        ║            └─→ For each scenario w JSON:
        ║                └→ Tworzy stub:
        ║                   "INV-001" → 200 + JSON
        ║                   "INV-002" → 200 + JSON
        ║                   "INV-404" → 404 + ERROR
        ║
        ║  when: request GET /api/documents/INV-001?includeMetadata=true
        ║        request GET /api/documents/INV-404
        ║         └─→ WireMock zwraca z JSON
        ║
        ║  then: assert response.statusCode == 200
        ║         assert response.statusCode == 404
        ║
        ║  RESULT: PASSED ✅
        ╚═════════════════════════════════════════════════════╝
                            ↓

        ┌───────────────────────────────────────┐
        │  cleanupSpec() - RUN ONCE (after all)  │
        └───────────────────────────────────────┘
                            ↓
        documentApi.stop()
                            ↓
        ✅ WireMock serwer ZAMKNIĘTY
                            ↓

        RAPORT:
        ✅ 3 tests PASSED
        ✅ BUILD SUCCESS
```

---

## DIAGRAM 3: Manualny test curl (Developer workflow)

```
TERMINAL A                              TERMINAL B
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Komenda:                                
./gradlew.bat \
  runDocumentMockServer \              
  -PdocMockPort=8097                  
      ↓

main() DocumentApiMockServerRunner     
  │                                    
  ├─ port = 8097                       
  ├─ mockServer = fixedPort(8097)      
  ├─ mockServer.start()                
  ├─ mockServer.stubFromJsonFile(      
  │    "document-scenarios.json")       
  │   └─→ Czyta JSON:                  
  │       "INV-001": 200 + JSON         
  │       "INV-404": 404 + ERROR        
  │                                    
  └─ Output:                           
     ✅ Mock server started on          
         http://localhost:8097          
     ✅ Loaded scenarios: 3             
     ✅ Press Ctrl+C to stop.          
     [WAITING...]                      
           ↓                                        
           │                           ← TERAZ TY WPISUJESZ W INNYM TERMINALU
           │
           │                           curl.exe "http://localhost:8097/api/documents/INV-001"
           │                               ↓
           │                               │ HTTP GET request leci do WireMock
           │                               │
           ├──────────────────────────────→├ WireMock: "Czy mam stub dla INV-001? TAK!"
           │                               │
           │←──────────────────────────────├ WireMock: Zwracam 200 + JSON
           │                               │
           │                           {"id":"INV-001","status":"READY",...}
           │                               ↓
           │                           curl wydrukuje JSON ✅
           │
           │ [WAITING NA NASTĘPNY REQUEST]
           │
           │                           curl.exe "http://localhost:8097/api/documents/INV-404"
           │                               ↓
           ├──────────────────────────────→├ WireMock: "INV-404 nie istnieje → 404"
           │                               │
           │←──────────────────────────────├ WireMock: Zwracam 404 + ERROR JSON
           │                               │
           │                           {"error":"DOCUMENT_NOT_FOUND"}
           │                               ↓
           │                           curl wydrukuje ERROR ✅
           │
           │ [WAITING...]
           │
           │                           (ty naciskasz Ctrl+C)
           │                               ↓
           Ctrl+C lub timeout              (stop)
           │
           mockServer.stop()
           │
           ✅ WireMock shutdown
```

---

## DIAGRAM 4: Logika klasy DocumentApiMockServer

```
┌─────────────────────────────────────────────────────────────────┐
│                 DocumentApiMockServer                            │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Prywatne pole:                                          │  │
│  │  private final WireMockServer server                     │  │
│  │  (To jest rzeczywisty serwer HTTP)                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  void start()                                             │  │
│  │  └─→ server.start()                                      │  │
│  │      ✅ WireMock słucha na porcie                        │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  void reset()                                             │  │
│  │  └─→ server.resetAll()                                   │  │
│  │      ✅ Usuwam wszystkie poprzednie stuby               │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  void stubDocumentOk(String id, boolean includeMetadata) │  │
│  │  └─→ server.stubFor(                                     │  │
│  │      GET /api/documents/{id}                            │  │
│  │      .withQueryParam("includeMetadata", "true")         │  │
│  │      .willReturn(200 + JSON)                            │  │
│  │      )                                                  │  │
│  │      ✅ Dodaję stub do WireMock                         │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  int stubFromJsonFile(File jsonFile)               ★ NOWE   │
│  │  ├─→ Parse JSON: { "scenarios": [...] }                 │  │
│  │  ├─→ For each scenario:                                 │  │
│  │  │   └─→ stubSingleScenario(scenario)                   │  │
│  │  │       ├─ id = scenario.id                            │  │
│  │  │       ├─ statusCode = scenario.statusCode            │  │
│  │  │       ├─ body = scenario.body (lub bodyText)         │  │
│  │  │       └─ server.stubFor(...)                         │  │
│  │  └─→ return liczba wczytanych scenariuszy               │  │
│  │      ✅ Łatwo: zamiast ręczny .stubDocumentOk() x 100  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  void verifyDocumentRequested(String id, ...)            │  │
│  │  └─→ server.verify(getRequestedFor(...))                │  │
│  │      ✅ Sprawdziłem, czy request rzeczywiście poleciał  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## DIAGRAM 5: Kontrakt API (Co testujemy?)

```
REQUEST (to, co wyślemy):
═══════════════════════════════════════════════════

GET /api/documents/INV-001?includeMetadata=true HTTP/1.1
Host: localhost:8089
User-Agent: Java-HttpClient/11


RESPONSE (to, co spodziewamy się otrzymać):
═══════════════════════════════════════════════════

HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "INV-001",
  "status": "READY",
  "owner": "JAN_KOWALSKI",
  "contentType": "application/pdf",
  "downloadUrl": "https://documents.example.local/files/INV-001.pdf"
}


CO TEST SPRAWDZA:
═══════════════════════════════════════════════════

✓ Status = 200 (sukces)
✓ Content-Type = JSON
✓ JSON zawiera pola: id, status, owner, contentType, downloadUrl
✓ Wartości są poprawne (id == "INV-001", owner == "JAN_KOWALSKI")
✓ WireMock zalogował request (verify)
✓ Request zawierał query param ?includeMetadata=true
```

---

## DIAGRAM 6: Przepływ danych w JSON (autogeneracja)

```
┌──────────────────────────────────────────────────────────────────┐
│  PLIK: document-scenarios.json                                    │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  {                                                                │
│    "scenarios": [                 ← lista scenariuszy             │
│      ┌───────────────────────────────────────────────────────┐  │
│      │ Scenariusz 1: Dokument istnieje (200)                │  │
│      │ ┌─────────────────────────────────────────────────┐  │  │
│      │ │ "id": "INV-2026-05-001"                         │  │  │
│      │ │ "includeMetadata": true                         │  │  │
│      │ │ "statusCode": 200                               │  │  │
│      │ │ "body": {                                       │  │  │
│      │ │   "id": "INV-2026-05-001",                      │  │  │
│      │ │   "status": "READY",                            │  │  │
│      │ │   "owner": "JAN_KOWALSKI"                       │  │  │
│      │ │ }                                               │  │  │
│      │ └─────────────────────────────────────────────────┘  │  │
│      └───────────────────────────────────────────────────────┘  │
│      ┌───────────────────────────────────────────────────────┐  │
│      │ Scenariusz 2: Dokument istnieje, bez metadanych (200)│  │
│      │ ┌─────────────────────────────────────────────────┐  │  │
│      │ │ "id": "INV-2026-05-002"                         │  │  │
│      │ │ "includeMetadata": false                        │  │  │
│      │ │ "statusCode": 200                               │  │  │
│      │ │ "body": { ... }                                 │  │  │
│      │ └─────────────────────────────────────────────────┘  │  │
│      └───────────────────────────────────────────────────────┘  │
│      ┌───────────────────────────────────────────────────────┐  │
│      │ Scenariusz 3: Dokument nie istnieje (404)            │  │
│      │ ┌─────────────────────────────────────────────────┐  │  │
│      │ │ "id": "INV-404"                                 │  │  │
│      │ │ "includeMetadata": false                        │  │  │
│      │ │ "statusCode": 404                               │  │  │
│      │ │ "body": {                                       │  │  │
│      │ │   "error": "DOCUMENT_NOT_FOUND"                 │  │  │
│      │ │ }                                               │  │  │
│      │ └─────────────────────────────────────────────────┘  │  │
│      └───────────────────────────────────────────────────────┘  │
│    ]                                                              │
│  }                                                                │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
        │
        │ mockApi.stubFromJsonFile(jsonFile)
        ↓
┌──────────────────────────────────────────────────────────────────┐
│  WIREMOCK - Automatycznie dodaje stuby:                          │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  stub 1:                                                          │
│  GET /api/documents/INV-2026-05-001?includeMetadata=true         │
│  ↓ (gdy przychodzi request)                                      │
│  200 OK + JSON                                                    │
│                                                                   │
│  stub 2:                                                          │
│  GET /api/documents/INV-2026-05-002                              │
│  ↓                                                                │
│  200 OK + JSON                                                    │
│                                                                   │
│  stub 3:                                                          │
│  GET /api/documents/INV-404                                      │
│  ↓                                                                │
│  404 NOT_FOUND + ERROR JSON                                      │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Wyświetl ten plik w czytelniku:

```powershell
Get-Content scripts/SCHEMAT--Testowanie-API-dokumentow-dengan-MockServer.md | less
```

lub po prostu otworz w edytorze:
```powershell
code scripts/SCHEMAT--Testowanie-API-dokumentow-dengan-MockServer.md
```

