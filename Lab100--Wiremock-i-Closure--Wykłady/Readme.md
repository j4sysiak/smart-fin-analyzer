Lab101--Wiremock-i-Closure

## 1) Closure w Wiremock
Closure w Groovy to „blok kodu”, który:
 - można trzymać w zmiennej
 - przekazywać jako parametr i wykonać później
W testach i przy WireMocku to jest bardzo wygodne, bo pozwala budować małe, czytelne „klocki” do stubowania i weryfikacji.

## 2) Co to jest closure?
Closure to coś w rodzaju funkcji anonimowej, ale w Groovy jest bardziej elastyczna niż w wielu innych językach.
Closure może:
 - przyjmować argumenty
 - używać zmiennych z zewnętrznego zakresu
 - być przypisana do zmiennej
 - być przekazana do innej metody
 - być wykonana później

przykład (tutaj greet nie jest metodą klasy, tylko closure, który można przypisać do zmiennej i wywołać):
```groovy
def greet = { name ->
    "Cześć, $name"
}
println greet("Jacek") // wypisze: Cześć, Jacek`
```

## 3) Dlaczego to jest przydatne w testach?
Bo możesz zbudować sobie małe funkcje do:
 - tworzenia stubów:
        przykład w klasie `DocumentApiMockServer.groovy`:
        
        server.stubFor(requestBuilder.willReturn(
                    aResponse()
                    .withStatus(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)))
  
 - generowania odpowiedzi JSON
 - rejestrowania wielu scenariuszy
 - powtarzalnych asercji
 
Zamiast pisać ciągle to samo:
```groovy
    mockServer.stubFor(
        get(urlEqualTo("/api/documents/INV-001"))
            .willReturn(aResponse().withStatus(200))
    )
    mockServer.stubFor(
        get(urlEqualTo("/api/documents/INV-002"))
            .willReturn(aResponse().withStatus(200))
    )
    mockServer.stubFor(
        get(urlEqualTo("/api/documents/INV-404"))
            .willReturn(aResponse().withStatus(404))
    )
```

możesz zrobić Closure, które przyjmuje scenariusz i sama robi stub.

## 4) Jak closure wygląda z WireMockiem?
WireMock sam w sobie nie wymaga Closure, ale w Groovy Closure świetnie nadaje się do opakowania konfiguracji WireMocka.
   Przykład idei:

```groovy
def stubDocument = { String id, int status, Map body ->
    mockServer.stubFor(
        get(urlPathEqualTo("/api/documents/$id"))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(groovy.json.JsonOutput.toJson(body))
            )
    )
}
```

I potem używasz tak:
```groovy
stubDocument("INV-001", 200, [id: "INV-001", status: "READY"])
stubDocument("INV-404", 404, [error: "DOCUMENT_NOT_FOUND"])
```


### Praktyczny wariant „w kodzie” 
C:\dev\smart-fin-analyzer\src\test\groovy\pl\edu\praktyki\wiremock

Jeśli chcesz zobaczyć, jak to wygląda bardziej realistycznie, to w helperze testowym możesz mieć np. coś takiego:

```groovy
import groovy.json.JsonOutput

class DocumentApiMockServer {

    def mockServer

    def stubDocument = { String id, int status, Map body ->
        mockServer.stubFor(
            get(urlPathEqualTo("/api/documents/${id}"))
                .willReturn(
                    aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(JsonOutput.toJson(body))
                )
        )
    }

    void registerBasicScenarios() {
        stubDocument("INV-001", 200, [
            id: "INV-001",
            status: "READY",
            amount: 1250.50,
            currency: "PLN"
        ])

        stubDocument("INV-002", 200, [
            id: "INV-002",
            status: "PROCESSING",
            amount: 999.99,
            currency: "EUR"
        ])

        stubDocument("INV-404", 404, [
            error: "DOCUMENT_NOT_FOUND",
            message: "Document INV-404 does not exist"
        ])
    }
}
```

Wtedy test robi się bardzo prosty:

```groovy
    def "powinien zwrócić READY dla INV-001 oraz 404 dla INV-404"() {
    given:
    // Rejestrujemy scenariusze w mockowanym serwerze dokumentów
    // Rejestrujemy odpowiedzi dla istniejącego dokumentu oraz przypadku 404.
    documentApiMockServer.registerBasicScenarios()

    when:
    def ready = fetch("${mockServer.baseUrl()}/api/documents/INV-001")
    def missing = fetch("${mockServer.baseUrl()}/api/documents/INV-404")

    then:
    ready.status == 200
    ready.body.status == "READY"
    ready.body.currency == "PLN"

    missing.status == 404
    missing.body.error == "DOCUMENT_NOT_FOUND"
}

private static Map fetch(String url) {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection()
    conn.requestMethod = "GET"
    int status = conn.responseCode

    InputStream stream = status >= 400 ? conn.errorStream : conn.inputStream
    String text = stream?.getText("UTF-8")
    Map body = text ? (Map) new JsonSlurper().parseText(text) : [:]

    [status: status, body: body]
}
```



Czyli punkt 4 w praktyce oznacza:
- closure `stubDocument` ukrywa cały techniczny `WireMock` boilerplate,
- w teście operujesz już na scenariuszach biznesowych,
- dodanie nowego przypadku to tylko kolejne wywołanie `stubDocument(...)`.

Co tu zyskujesz?
 - mniej duplikacji,
 - czytelniejszy test,
 - łatwo dodajesz kolejne scenariusze,
 - łatwo przenosisz logikę do helpera






## 5) Closure jako „mini-DSL” do scenariuszy
W Groovy closure często robi się po to, żeby testy wyglądały bardziej „językowo”.
Na przykład możesz zrobić coś takiego:

```groovy
def scenario = { Map s ->
    if (s.statusCode == 200) {
        mockDocumentOk(s.id, s.includeMetadata, s.body)
    } else {
        mockDocumentNotFound(s.id)
    }
}
```

A potem:
```groovy
scenarios.each { scenario(it) }
```

To jest bardzo czytelne:
weź scenariusz → zrób z niego stub.


## 6) Jak closure pomaga przy JSON scenariuszach?
To jest chyba najbliższe Twojemu przypadkowi.
Masz plik JSON typu:
 - dokument istnieje,
 - dokument nie istnieje,
 - dokument zwraca błąd,
 - dokument odpowiada z opóźnieniem.

I chcesz, żeby serwer mockujący automatycznie je zarejestrował.
   Wtedy closure może wyglądać tak:

```groovy
def registerScenario = { Map s ->
    def path = "/api/documents/${s.id}"
    def mapping = get(urlPathEqualTo(path))

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
```

I potem:
```groovy
scenarios.each(registerScenario)
```
Sens:
 - closure registerScenario zawiera całą logikę „jak zamienić jeden scenariusz na stub”,
 - możesz ją łatwo wykorzystywać w wielu testach,
 - test staje się krótszy i bardziej opisowy.


## 7) Czym closure różni się od metody?
W praktyce:
   Metoda:
    - jest częścią klasy,
    - ma nazwę,
    - jest bardziej „formalna”

```groovy
void stubDocument(String id) {
    // ...
}
```

  Closure
   - możesz ją trzymać w zmiennej,
   - przekazywać dalej,
   - tworzyć lokalnie w teście,
   - łatwo robić z niej mały kawałek DSL

```groovy
def stubDocument = { String id ->
    // ...
}
```

W testach Groovy closure często daje większą elastyczność.

## 8) Jak możesz to wykorzystać u siebie?
W Twoim przypadku closure ma sens w 3 miejscach:

A. Generator stubów z JSON
   Zamiast ręcznie stubować dokumenty, robisz closure, która bierze jeden rekord scenariusza i ustawia WireMocka.
   
B. Reużywalne helpery do testów
   Możesz mieć np. closure do:
   - dokumentu poprawnego,
   - dokumentu brakującego,
   - dokumentu z błędem 500,
   - dokumentu z timeoutem.

C. Specyficzne zachowania:
    Np.:
    - jeśli request ma parametr includeMetadata=true, zwróć pełny JSON,
    - jeśli nie ma parametru, zwróć okrojony JSON,
    - jeśli id pasuje do wzorca, odpowiedz inaczej


## 9) Praktyczny przykład „u Ciebie”
   Wyobraź sobie pomocnika:

```groovy
def buildDocumentStub = { Map s ->
    def url = "/api/documents/${s.id}"
    def request = get(urlPathEqualTo(url))

    if (s.includeMetadata) {
        request = request.withQueryParam("includeMetadata", equalTo("true"))
    }

    mockServer.stubFor(
        request.willReturn(
            aResponse()
                .withStatus(s.statusCode)
                .withHeader("Content-Type", "application/json")
                .withBody(groovy.json.JsonOutput.toJson(s.body))
        )
    )
}
```

i potem w teście:

```groovy
scenarios.each { scenario ->
    buildDocumentStub(scenario)
}
```
To jest bardzo typowe Groovy:
closure jako funkcja pomocnicza + each do przetwarzania listy.


## 10) Kiedy closure jest lepsze niż zwykła metoda?
   Closure warto użyć, gdy:
   - logika jest lokalna tylko dla testu,
   - chcesz przekazać zachowanie jako parametr,
   - chcesz zbudować czytelny DSL,
   - chcesz łatwo iterować po scenariuszach.
   
   Metoda jest lepsza, gdy:
   - logika ma być współdzielona w wielu klasach,
   - chcesz formalną strukturę,
   - kod robi się duży i warto go przenieść do klasy pomocniczej.


## 11) Najkrótsze podsumowanie

Closure w Twoim przypadku to po prostu: mały, przekazywalny kawałek kodu do stubowania WireMocka, generowania scenariuszy i upraszczania testów.
    Czyli zamiast pisać ręcznie:
    stubDocumentOk(...)
    stubDocumentNotFound(...)
    stubDocumentTimeout(...)
    możesz zrobić jedną closure, która czyta scenariusz i sama robi odpowiedni stub.

np.:
```groovy
def registerScenario = { Map s ->
    if (s.statusCode == 200) {
        stubDocumentOk(s.id, s.includeMetadata, s.body)
    } else if (s.statusCode == 404) {
        stubDocumentNotFound(s.id)
    } else if (s.statusCode == 500) {
        stubDocumentError(s.id)
    }
}   
```
I potem:
```groovyscenarios.each { registerScenario(it) }
```
To jest właśnie siła closure w Groovy – pozwala Ci zbudować elastyczne i czytelne testy, które łatwo rozszerzać o nowe scenariusze.


## 12) Prawdziwy przykład w Twoim projekcie

Najbardziej naturalne miejsce na closure u Ciebie to `DocumentApiMockServer.groovy`,
czyli helper, który rejestruje stuby WireMocka dla endpointu dokumentów.

Załóżmy, że dziś masz metody w stylu:
- `mockDocumentOk(String id, boolean includeMetadata, Map body)`
- `mockDocumentNotFound(String id)`
- `mockDocumentError(String id)`

To możesz dołożyć jedną closure, która przyjmuje pojedynczy scenariusz i sama wybiera,
jakiego stubu użyć.

### Przykład implementacji w `DocumentApiMockServer.groovy`

```groovy
import groovy.json.JsonOutput

class DocumentApiMockServer {

    def server

    def registerDocumentScenario = { Map scenario ->
        String id = scenario.id as String
        int statusCode = scenario.statusCode as int
        boolean includeMetadata = (scenario.includeMetadata ?: false) as boolean
        Map body = (scenario.body ?: [:]) as Map

        def requestBuilder = get(urlPathEqualTo("/api/documents/${id}"))

        if (includeMetadata) {
            requestBuilder = requestBuilder.withQueryParam("includeMetadata", equalTo("true"))
        }

        def responseBuilder = aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")

        if (!body.isEmpty()) {
            responseBuilder = responseBuilder.withBody(JsonOutput.toJson(body))
        }

        if (scenario.fixedDelayMs) {
            responseBuilder = responseBuilder.withFixedDelay(scenario.fixedDelayMs as int)
        }

        server.stubFor(requestBuilder.willReturn(responseBuilder))
    }

    void registerDocumentScenarios(List<Map> scenarios) {
        scenarios.each(registerDocumentScenario)
    }
}
```

To jest dobry przykład, bo:
- closure jest lokalna dla mock servera,
- bierze dokładnie **jeden scenariusz**, więc łatwo ją testować i czytać,
- `each(registerDocumentScenario)` daje bardzo groovy'owy, zwięzły zapis.

## 13) Jak wygląda lista scenariuszy

W teście możesz przygotować dane tak:

```groovy
def scenarios = [
    [
        id: "INV-001",
        statusCode: 200,
        includeMetadata: true,
        body: [
            id: "INV-001",
            status: "READY",
            metadata: [source: "ERP", owner: "FINANCE"]
        ]
    ],
    [
        id: "INV-002",
        statusCode: 200,
        includeMetadata: false,
        body: [
            id: "INV-002",
            status: "READY"
        ]
    ],
    [
        id: "INV-404",
        statusCode: 404,
        body: [error: "DOCUMENT_NOT_FOUND"]
    ],
    [
        id: "INV-500",
        statusCode: 500,
        body: [error: "DOCUMENT_SERVICE_ERROR"]
    ],
    [
        id: "INV-SLOW",
        statusCode: 200,
        fixedDelayMs: 2500,
        body: [id: "INV-SLOW", status: "READY"]
    ]
]
```

I potem po prostu:

```groovy
documentApiMockServer.registerDocumentScenarios(scenarios)
```

## 14) Użycie w teście integracyjnym

W testach integracyjnych albo komponentowych może to wyglądać tak:

```groovy
def "powinien pobrać status dokumentu i obsłużyć brakujący dokument"() {
    given:
    def scenarios = [
        [id: "INV-001", statusCode: 200, body: [id: "INV-001", status: "READY"]],
        [id: "INV-404", statusCode: 404, body: [error: "DOCUMENT_NOT_FOUND"]]
    ]
    documentApiMockServer.registerDocumentScenarios(scenarios)

    when:
    def existing = documentFacade.fetchDocument("INV-001")
    def missing = documentFacade.fetchDocument("INV-404")

    then:
    existing.status == "READY"
    missing == null
}
```

Nie chodzi tu o idealne dopasowanie 1:1 do sygnatur w Twoich klasach,
tylko o pokazanie realnego wzorca, który możesz wsadzić do projektu praktycznie bez zmian.

## 15) Wersja bardziej „smart” — closure jako dispatcher

Jeśli chcesz zachować już istniejące, bardziej opisowe metody helperowe,
to closure może tylko wybierać odpowiednią ścieżkę:

```groovy
def registerScenario = { Map s ->
    if ((s.statusCode as int) == 200) {
        mockDocumentOk(s.id as String, (s.includeMetadata ?: false) as boolean, s.body as Map)
    } else if ((s.statusCode as int) == 404) {
        mockDocumentNotFound(s.id as String)
    } else if ((s.statusCode as int) == 500) {
        mockDocumentError(s.id as String)
    } else {
        throw new IllegalArgumentException("Unsupported document scenario: ${s}")
    }
}

scenarios.each(registerScenario)
```

Ta wersja jest świetna, gdy:
- masz już gotowe helpery,
- nie chcesz przepisywać całego mock servera,
- chcesz tylko dodać warstwę, która mapuje JSON/scenariusz na konkretne stuby.

## 16) Dlaczego to pasuje właśnie do Twojego projektu?

Bo u Ciebie przewijają się te same potrzeby:
- dużo scenariuszy testowych,
- integracja HTTP/WireMock,
- chęć ograniczenia duplikacji,
- Groovy, więc `Closure + each + Map` są naturalne.

W praktyce najlepszy pierwszy krok u Ciebie to:
1. zostawić istniejące metody `mockDocumentOk/mockDocumentNotFound/mockDocumentError`,
2. dodać nad nimi jedną closure `registerScenario`,
3. przekazywać do niej listę map z testu albo z pliku JSON.

To daje prosty zysk:
- krótsze testy,
- łatwiejsze dokładanie nowych przypadków,
- mniej ręcznego `stubFor(...)` w każdym teście.

## 17) Najkrótsza wersja do wdrożenia od razu

Jeżeli chcesz zrobić to minimalnym kosztem, to właśnie ten wariant jest najbardziej praktyczny:

```groovy
def registerScenario = { Map s ->
    if ((s.statusCode as int) == 200) {
        mockDocumentOk(s.id as String, (s.includeMetadata ?: false) as boolean, s.body as Map)
    } else if ((s.statusCode as int) == 404) {
        mockDocumentNotFound(s.id as String)
    } else if ((s.statusCode as int) == 500) {
        mockDocumentError(s.id as String)
    }
}

scenarios.each { registerScenario(it) }
```

To już jest prawdziwy, projektowy use-case closure — nie zabawka, tylko coś,
co realnie upraszcza `WireMock` w Twoich testach.
