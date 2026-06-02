# Schemat testowania API dokumentów z MockServer — kompletny proces

## 1) ARCHITEKTURA — Jak elementy się łączą?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│  TWÓJ  S Y S T E M   (np. SmartFinAnalyzer)                             │
│                                                                         │
│  W procedurze operacyjnej musisz pobrać dokument z obcego systemu:      │
│                                                                         │
│       def document = externalDocumentService.getDocument("INV-001")     │
│                                          ↓                              │
│                                    HTTP GET                             │
│                       http://external-api.com/api/documents/INV-001     │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
                                    │
                    ┌───────────────────────────────┐
                    │   PRAWDZIWY SYSTEM            │
                    │   (w produkcji)               │
                    │                               │
                    │ Może być:                     │
                    │ • Offline 🔴                  │
                    │ • Slow (5s odpowiedź) 🐌      │
                    │ • Rate-limited ⛔             │
                    │ • Drogi w utrzymaniu          │
                    └───────────────────────────────┘
                                    ↓
                    To jest problem w testach! ❌

═════════════════════════════════════════════════════════════════════════════

                        ROZWIĄZANIE: MockServer

┌──────────────────────────────────────────────────────────────────────────┐
│                                                                           │
│  ZAMIAST → polegać na prawdziwym API                                     │
│  ROBIMY    → uruchamiamy FAKE serwer HTTP (WireMock)                     │
│             na LOCALHOST, pod KONTROLĄ testu                            │
│                                                                           │
│        ┌─────────────────────────────────────────────────────────┐       │
│        │  WireMock - FAKE API Serwer (na porcie 8089)           │       │
│        │                                                         │       │
│        │  Definicja: "Gdy dostanę GET /api/documents/INV-001"  │       │
│        │             "Zwróć JSON: { id: INV-001, ... }"        │       │
│        │                                                         │       │
│        │  ✅ Zawsze dostępny                                    │       │
│        │  ✅ Natychmiast odpowiada (<1ms)                       │       │
│        │  ✅ Możliwy do sterowania (status 200, 404, etc)      │       │
│        │  ✅ Testowy                                             │       │
│        └─────────────────────────────────────────────────────────┘       │
│                         ↑                                                 │
│                    HTTP GET                                              │
│        http://localhost:8089/api/documents/INV-001                      │
│                                                                           │
│        Twój system myśli, że rozmawia z prawdziwym API,                 │
│        ale faktycznie rozmawia z FAKE serwerem (WireMock)               │
│                                                                           │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 2) TRZY SPOSOBY UŻYCIA MockServer

### Sposób A: TEST SPOCK (automatyczny)

```
┌─────────────────────────────────────────────────────────────────┐
│  Uruchomienie: ./gradlew.bat documentMockTest                   │
└─────────────────────────────────────────────────────────────────┘
                            ↓
                            │
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│  KROK 1: DocumentProviderMockServerSpec.setupSpec()             │
│           └─→ Uruchamiam WireMock server:                       │
│               DocumentApiMockServer mockApi = new ...()         │
│               mockApi.start()  ← Serwer HTTP słucha na :8089    │
│                                                                  │
│  KROK 2: setup() [przed każdym testem]                          │
│           └─→ mockApi.reset()  ← Czyszczę poprzednie stuby     │
│                                                                  │
│  KROK 3: Test "powinien pobrać dokument..."                    │
│           └─→ given: mockApi.stubDocumentOk("INV-001", true)    │
│               ✓ Definiuję fake endpoint                         │
│               ✓ "Wtedy zwrócisz mi JSON dokument"              │
│                                                                  │
│           └─→ when: HttpRequest GET /api/documents/INV-001      │
│               ✓ Wysyłam PRAWDZIWY HTTP request do localhost    │
│               ✓ WireMock przechwytuje, zwraca stubbed JSON     │
│                                                                  │
│           └─→ then: asercje na typie, polu "id", etc           │
│               ✓ Weryfikuję, że:                                 │
│                 • Status = 200                                  │
│                 • JSON zawiera dobry ID, owner, etc             │
│                 • Request rzeczywiście poleciał do serwera      │
│                                                                  │
│  KROK 4: cleanupSpec()                                          │
│           └─→ mockApi.stop()  ← Wyłączam WireMock              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Sposób B: MANUALNY CURL (do szybkiego sprawdzenia lokalnie)

```
┌──────────────────────────────────────────────────────────────────┐
│  Uruchomienie: ./gradlew.bat runDocumentMockServer               │
│                           -PdocMockPort=8097                    │
└──────────────────────────────────────────────────────────────────┘
                            ↓
                            │
┌──────────────────────────────────────────────────────────────────┐
│                                                                   │
│  main() klasy DocumentApiMockServerRunner:                        │
│                                                                   │
│  1. Wczytaj port: 8097                                           │
│  2. Stwórz WireMock: new DocumentApiMockServer.fixedPort(8097)  │
│  3. Załaduj scenariusze z JSON:                                 │
│     mockServer.stubFromJsonFile("document-scenarios.json")       │
│     └─→ Czyta plik, for each scenariusz (INV-001, INV-404, ...) │
│         └─→ Dodaje stub do serwera (200 OK, 404, etc)          │
│  4. Start: mockServer.start()                                    │
│  5. Wypisz: "Server started on http://localhost:8097"           │
│  6. Czekaj w nieskończonej pętli (aż Ctrl+C)                    │
│                                                                   │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                                   │
│  Ty teraz (w innym terminalu):                                   │
│                                                                   │
│  curl.exe http://localhost:8097/api/documents/INV-001            │
│  │                                                               │
│  └─→ WireMock przechwyci request                                │
│      └─→ Znajdzie stub dla "INV-001"                            │
│          └─→ ZwróciJSON z document-scenarios.json               │
│              └─→ curl wydrukuje JSON na ekranie ✓               │
│                                                                   │
│  curl.exe http://localhost:8097/api/documents/INV-404            │
│  │                                                               │
│  └─→ WireMock zwróci 404 DOCUMENT_NOT_FOUND                     │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Sposób C: WIELU SCENARIUSZY Z JSON (batch)

```
┌───────────────────────────────────────────────────────────────┐
│  Plik: src/test/resources/mock/document-scenarios.json        │
│                                                               │
│  {                                                            │
│    "scenarios": [                                            │
│      { "id": "INV-001", "statusCode": 200, "body": {...} }, │
│      { "id": "INV-002", "statusCode": 200, "body": {...} }, │
│      { "id": "INV-404", "statusCode": 404, "body": {...} },  │
│      { "id": "TIMEOUT", "statusCode": 500, "body": {...} }   │
│    ]                                                          │
│  }                                                            │
└───────────────────────────────────────────────────────────────┘
                            ↓
        mockServer.stubFromJsonFile(jsonFile)
                            ↓
        For each scenario w ["INV-001", "INV-002", ...]
                            ↓
        Automatycznie dodaj stub do WireMock:
        
        ✓ GET /api/documents/INV-001 → 200 + JSON
        ✓ GET /api/documents/INV-002 → 200 + JSON
        ✓ GET /api/documents/INV-404 → 404 + ERROR
        ✓ GET /api/documents/TIMEOUT  → 500 + ERROR
        
        (zamiast ręcznego mockApi.stubDocumentOk("INV-001", true))
```

---

## 3) SENS TESTOWANIA — Co chcemy osiągnąć?

### ❌ Problem bez MockServer:

```
Test testDataFetch() {
  call: externalService.getDocument("INV-001")
  
  Czego muszę mieć:
  • Prawdziwy system dokumentów ONLINE
  • Muszę znać login/hasło
  • Odpowiedź 5s (wolna)
  • Duplikuję dane testowe (INV-001, INV-002, ...)
  • Test się sypie, jeśli serwer downi (flaky test)
  • Drogi CI/CD (płacę za external API calls)
  • Ryzyko: mogę przypadkiem usunąć dokument PROD 💀
}
```

### ✅ Rozwiązanie z MockServer:

```
Test testDataFetch() {
  // 1. Zamiast → call real API
  //    Robię    → start fake WireMock server
  mockServer.start()
  
  // 2. Zamiast → czekaj 5 sekund na response
  //    Robię    → WireMock zwraca natychmiast (<1ms)
  mockServer.stubDocumentOk("INV-001", true)
  
  // 3. Zamiast → dane w production API
  //    Robię    → dane w memory (JSON file)
  // Mogę testować scenario bez dostępu do internetu
  
  // 4. Zamiast → flaky test (serwer offline = fail)
  //    Robię    → kontroluję wszystko → test stable ✓
  mockServer.stubDocumentNotFound("NOT-EXIST")
  
  // 5. Zamiast → niepewność (czy to moje dane?)
  //    Robię    → weryfikuję kontrakt (verify request)
  mockServer.verify("GET", "/api/documents/INV-001")
  
  // 6. Zamiast → drogi CI/CD
  //    Robię    → testy są praktycznie darmowe
  mockServer.stop()
}

✅ SZYBKIE (milliseconds)
✅ NIEZAWODNE (zawsze deterministic)
✅ TANIE (nie płacę za external API)
✅ BEZPIECZNE (nie dotykam production danych)
✅ KONTROLOWALNE (mogę testować edge-case, błędy, timeout)
```

---

## 4) KROKI TESTOWANIA — Praktyczny przepływ

### Scenariusz 1: Test Spock (automatyczny)

```
1. TEST STARTUP
   ├─ setupSpec() uruchamia WireMock na :8089
   ├─ Serwer gotowy do pracy

2. KAŻDY TEST (np. "powinien pobrać dokument")
   ├─ setup() czyszczę stare stuby
   ├─ given: definiuję stub
   │   mockApi.stubDocumentOk("INV-001", true)
   │   └─→ "Gdy przychodzi GET /api/documents/INV-001?includeMetadata=true"
   │       "Zwróć 200 + JSON dokument"
   │
   ├─ when: wysyłam HTTP request
   │   response = httpClient.send(GET /api/documents/INV-001?...)
   │   └─→ Request trafia do WireMock
   │       WireMock patrzy: "Mam stub dla INV-001? Tak!"
   │       WireMock 200 + JSON
   │
   ├─ then: sprawdzam asercje
   │   assert response.statusCode == 200
   │   assert response.body.id == "INV-001"
   │   assert response.body.owner == "JAN_KOWALSKI"
   │   mockApi.verify("GET", "/api/documents/INV-001")
   │   └─→ "Czy rzeczywiście przyszedł taki request?"

3. TEST CLEANUP
   └─ cleanupSpec() wyłącza WireMock

════════════════════════════════════════════════════════════════════

Efekt: JUnit raportuje PASS ✓

```

### Scenariusz 2: Manualny test (developer)

```
1. TERMINAL A — Start mock serwera
   ./gradlew.bat runDocumentMockServer -PdocMockPort=8097
   
   Output:
   > Task :runDocumentMockServer
   Document API mock server started on http://localhost:8097
   Loaded scenarios: 3 from ./src/test/resources/mock/document-scenarios.json
   Try: curl.exe "http://localhost:8097/api/documents/INV-2026-05-001?includeMetadata=true"
   Press Ctrl+C to stop.
   
   ← Serwer czeka na requesty

2. TERMINAL B — Testuję curl
   curl.exe "http://localhost:8097/api/documents/INV-2026-05-001?includeMetadata=true"
   
   Output:
   {
     "id": "INV-2026-05-001",
     "status": "READY",
     "owner": "JAN_KOWALSKI",
     "contentType": "application/pdf",
     "downloadUrl": "https://documents.example.local/files/INV-2026-05-001.pdf"
   }
   
   ✓ API zwrócił dokument

3. TERMINAL B — Test edge case (404)
   curl.exe "http://localhost:8097/api/documents/INV-404"
   
   Output:
   {"error":"DOCUMENT_NOT_FOUND"}
   
   ✓ API zwróci błąd dla brakującego dokumentu

4. TERMINAL A — Stop
   Ctrl+C
   
   Mock server shutdown ✓

Efekt: Zweryfikowałem, że kontrakt API jest OK 🎯
```

---

## 5) PODSUMOWANIE — CO SIĘ DZIEJE?

| Aspekt            | MockServer                          | Prawdziwy API          |
|-------------------|-------------------------------------|------------------------|
| **Dostępność**    | ✅ Zawsze (kod źródłowy)            | ❌ Offline = test fail |
| **Szybkość**      | ✅ <1ms                             | ❌ 1-5s latency        |
| **Koszt**         | ✅ Darmowy (localhost)              | ❌ Zapłacić za call    |
| **Bezpieczeństwo**| ✅ Test data (fake)                 | ❌ Ryzyko prod damage  |
| **Debugging**     | ✅ Pełna kontrola                   | ❌ Black box           |
| **Edge-case**     | ✅ Łatwo (stubuję 404, timeout)     | ❌ Nie mogę forsować    |
| **Kontrat API**   | ✅ Weryfikuję żeby był poprawny     | ❌ Ufam na słowo       |

---

## 6) PRAKTYCZNE PRZYPADKI UŻYCIA

### Use-Case 1: Testy CI/CD (Pull Request)

```
GitHub Actions uruchomia:
./gradlew.bat documentMockTest

✓ Brak zależności od external API
✓ Test trwa <1s zamiast 10s
✓ Darmowy (nie płacę za każde uruchomienie)
✓ Stabilny (nie ma timeoutów)
```

### Use-Case 2: Developer robi feature (Local)

```
1. Piszę kod: externalService.fetchDocument("INV-001")
2. Chcę szybko testować: 
   ./gradlew.bat runDocumentMockServer
3. Testuję w Postmanie/curl
4. Uruchamiam unit test:
   ./gradlew.bat documentMockTest
5. Jeśli PASS → commit
```

### Use-Case 3: Testowanie tolerancji błędów

```
JSON scenariusze mogę rozszerzyć:

{
  "id": "TIMEOUT",
  "delayMs": 5000,        ← Zwróć odpowiedź po 5s
  "statusCode": 502,       ← Symuluj timeout
  "body": { "error": ... }
}

Test Spock może sprawdzić:
"Czy system retry-uje jeśli API timeout-uje?"
"Czy loguje błąd jeśli API 502?"
```

---

## 7) STRESZCZENIE

```
TESTOWANIE API DOKUMENTÓW to:

┌─────────────────────────────────────────────────────┐
│                                                     │
│  1. PROBLEM: Nie chcę polegać na prawdziwym API    │
│             (offline, powolne, drogi, niebezpieczne)│
│                                                     │
│  2. ROZWIĄZANIE: Zamiast prawdziwego API             │
│                 Tworzę FAKE serwer HTTP             │
│                 (WireMock) na LOCALHOST             │
│                                                     │
│  3. DEFINICJA: "Gdy przychodzi GET /api/docs/1"    │
│                "Zwróć 200 + JSON"                  │
│                                                     │
│  4. TEST: Wysyłam HTTP request do FAKE serwera      │
│           Weryfikuję odpowiedź                      │
│           Sprawdzam kontrakt API                    │
│                                                     │
│  5. KORZYŚCI:                                       │
│     ✓ Szybki (ms, nie s)                           │
│     ✓ Niezawodny (deterministic)                   │
│     ✓ Tani (brak external calls)                   │
│     ✓ Bezpieczny (nie prod data)                   │
│     ✓ Kontrolowalny (mogę losować błędy)           │
│                                                     │
└─────────────────────────────────────────────────────┘
```


