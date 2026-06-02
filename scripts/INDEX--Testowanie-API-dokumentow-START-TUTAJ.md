# 📚 INDEKS: Testowanie API dokumentów z MockServer

Żeby w pełni zrozumieć proces testowania, czytaj dokumenty w tej kolejności:

---

## 🎯 ZACZNIJ TUTAJ (5 minut)

### 1. **QUICK-REFERENCE--Klasy-i-ich-role.md** 
   📄 **TL;DR — najważniejsze rzeczy w pigułce**
   
   Zawiera:
   - Tabelka "Co robi każda klasa?"
   - Przepływ: JAK to działa (diagram prosty)
   - Trzy sposoby użycia (kiedy co robić?)
   - Step-by-step: Jak zacząć?
   - FAQ (odpowiedzi na pytania)
   
   **Czytasz jeśli:** Chcesz szybko wiedzieć "co po co"

---

## 🧠 ZROZUMIESZ LOGIKĘ (15 minut)

### 2. **SCHEMAT--Testowanie-API-dokumentow-dengan-MockServer.md**
   📖 **Pełne wyjaśnienie procesu**
   
   Zawiera:
   - Architektura: Gdzie żyje WireMock?
   - Sense testowania: Problem → Rozwiązanie
   - Kroki testowania: What happens when you test?
   - Praktyczne przypadki użycia
   - Streszczenie: Co się dzieje?
   
   **Czytasz jeśli:** Chcesz zrozumieć "dlaczego to działa tak a nie inaczej"

---

## 📊 ZOBACZYSZ WIZUALNIE (5 minut)

### 3. **DIAGRAMY--Testowanie-API-wizualne.md**
   🎨 **ASCII art i wizualne schematy**
   
   Zawiera:
   - Diagram 1: Architektura (gdzie żyje WireMock?)
   - Diagram 2: Przepływ testu Spock (krok po kroku)
   - Diagram 3: Manualny test curl (developer workflow)
   - Diagram 4: Logika klasy DocumentApiMockServer
   - Diagram 5: Kontrakt API (co testujemy?)
   - Diagram 6: Przepływ JSON scenariuszy
   
   **Czytasz jeśli:** Preferujesz rysunki zamiast tekstu

---

## 🔧 PRAKTYKA (5 minut)

### 4. **README--Mockowany-serwer-dokumentow-w-testach-groovy.md**
   ⚙️ **Jak uruchomić testy w praktyce**
   
   Zawiera:
   - Uruchomienie Spock testu
   - Uruchomienie manualnego curl
   - Przykłady komend
   - Format JSON scenariuszy
   - Troubleshooting
   
   **Czytasz jeśli:** Chcesz wiedzieć "które polecenie wpisać w terminal"

---

## 📁 GDZIE ZNALEŹĆ KOD?

```
src/test/groovy/pl/edu/praktyki/
├── support/mock/
│   ├── DocumentApiMockServer.groovy              ← Centrala kontroli WireMock
│   └── DocumentApiMockServerRunner.groovy        ← Launcher dla curl
│
└── integration/
    └── DocumentProviderMockServerSpec.groovy     ← Testy Spock

src/test/resources/mock/
└── document-scenarios.json                       ← Dane testowe (JSON)
```

---

## 🚀 SZYBKI START (Copy-paste ready)

### Test automatyczny:
```bash
cd C:\dev\smart-fin-analyzer
./gradlew.bat documentMockTest
```

### Manualny test (curl):
```bash
# Terminal A:
./gradlew.bat runDocumentMockServer

# Terminal B:
curl.exe "http://localhost:8089/api/documents/INV-2026-05-001?includeMetadata=true"
curl.exe "http://localhost:8089/api/documents/INV-404"
```

### Własny port:
```bash
./gradlew.bat runDocumentMockServer -PdocMockPort=8097
```

---

## 📚 MAPA CZYTANIA (zolecana kolejność)

```
START
  │
  ├─→ [5 min]  QUICK-REFERENCE (TL;DR)
  │             └─→ "Co to jest stub? Jakie są 3 sposoby?"
  │
  ├─→ [15 min] SCHEMAT (zrozumienie)
  │             └─→ "Dlaczego mock jest lepszy niż prawdziwe API?"
  │
  ├─→ [5 min]  DIAGRAMY (wizualizacja)
  │             └─→ "Jak przepływają dane?"
  │
  ├─→ [5 min]  README (praktyka)
  │             └─→ "Które polecenie uruchomić?"
  │
  └─→ [5 min]  Przeczytaj kod:
               src/test/groovy/.../DocumentApiMockServer.groovy
                │
                └─→ "Teraz widzę co się dzieje!"
               
END (Plamen? 🎉)
```

---

## 🎓 CO NAUCZYSZ SIĘ?

Po przeczytaniu wszystkich dokumentów:

1. ✅ Rozumiesz "czym jest WireMock"
2. ✅ Wiesz "jak zdefiniować stub"
3. ✅ Umiesz "napisać test Spock do API"
4. ✅ Potrafisz "ładować dane z JSON"
5. ✅ Znasz "3 sposoby testowania" (Spock, curl, batch)
6. ✅ Wiesz "dlaczego mock jest ważny"
7. ✅ Możesz "powielić wzorzec na inne API" (faktury, statusy, etc)

---

## 🔗 LINKI DO PLIKÓW

| Plik | Cel | Czytasz jeśli |
|------|-----|--------------|
| QUICK-REFERENCE--Klasy-i-ich-role.md | TL;DR | Chcesz szybko wiedzieć |
| SCHEMAT--Testowanie-API-dokumentow-dengan-MockServer.md | Wyjaśnienie | Chcesz zrozumieć "dlaczego" |
| DIAGRAMY--Testowanie-API-wizualne.md | Wizualizacja | Preferujesz rysunki |
| README--Mockowany-serwer-dokumentow-w-testach-groovy.md | Praktyka | Chcesz wiedzieć które komendy |

---

## ❓ MASZ PYTANIA?

Jeśli coś ci jest niejasne:

1. **Przeczytaj QUICK-REFERENCE** (może tam jest odpowiedź w FAQ)
2. **Szukaj w SCHEMACIE** pod sekcją "PRAKTYCZNE PRZYPADKI UŻYCIA"
3. **Rysunki w DIAGRAMACH** powinny to wyjaśnić
4. **Spróbuj sami** — `./gradlew.bat documentMockTest` + `curl`

---

## 🏁 SUMMARY

| Element | Odpowiadaj na |
|---------|--------------|
| **WireMock** | "Co to jest?" |
| **DocumentApiMockServer** | "Kto kontroluje WireMock?" |
| **DocumentApiMockServerRunner** | "Jak uruchomić WireMock lokalnie?" |
| **DocumentProviderMockServerSpec** | "Jak testować kontrakt?" |
| **JSON Scenarios** | "Jak załadować wiele dokumentów?" |
| **Stub** | "Co to jest definicja jeśli-wtedy?" |
| **Verify** | "Czy request faktycznie poleciał?" |

---

**Powodzenia! 🎯**

Jeśli chcesz, mogę dorzucić:
- Scenariusze z `delayMs` (timeout testing)
- Scenariusze z `fault` (chaos engineering)
- Integracja z IntelliJ (profiling)
- Load testing z WireMock


