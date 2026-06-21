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
 - tworzenia stubów,
 - generowania odpowiedzi JSON,
 - rejestrowania wielu scenariuszy,
 - powtarzalnych asercji.
 
Zamiast pisać ciągle to samo:
```groovy
   mockServer.stubFor(get(urlEqualTo("/api/documents/INV-001"))... )
   mockServer.stubFor(get(urlEqualTo("/api/documents/INV-002"))... )
   mockServer.stubFor(get(urlEqualTo("/api/documents/INV-404"))... )
```

możesz zrobić closure, które przyjmuje scenariusz i sama robi stub.

## 4) Jak closure wygląda z WireMockiem?
WireMock sam w sobie nie wymaga closure, ale w Groovy closure świetnie nadaje się do opakowania konfiguracji WireMocka.
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


Zadanie do wykonania:
w następnym kroku mogę Ci pokazać:
konkretną implementację closure pod Twój DocumentApiMockServer, albo
mały schemat „closure vs metoda vs klasy pomocnicze” na Twoim przykładzie.