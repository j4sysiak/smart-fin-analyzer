Lab 35
------

Brakuje jednej rzeczy, która w świecie komercyjnym jest absolutnie obowiązkowa.
Obecnie Twój endpoint POST /api/transactions ufa użytkownikowi bezgranicznie. 
Ktoś może wysłać JSON-a bez kwoty, bez opisu, a nawet z pustym ID, a system spróbuje to zapisać (co pewnie skończy się brzydkim błędem bazy danych).

Wchodzimy w Lab 35: Data Validation (Strażnik Danych).
------------------------------------------------------

Cel:
Zablokowanie "śmieciowych" danych na samym wejściu do `REST API` przy użyciu standardu `Bean Validation (JSR 380)`
i podpięcie tego pod nasz piękny `GlobalExceptionHandler`.


Krok 35.1: Dodanie zależności
Spring Boot ma gotowy starter do walidacji. Otwórz build.gradle i dodaj:

// Starter do walidacji danych wejściowych REST API
implementation 'org.springframework.boot:spring-boot-starter-validation'


Krok 35.2: Adnotacje walidacyjne w Modelu
-----------------------------------------

Otwórz plik `src/main/groovy/pl/edu/praktyki/domain/Transaction.groovy`.

Dodamy w nim zasady, jakich musi przestrzegać poprawna transakcja. (Zwróć uwagę na nowe importy z pakietu jakarta).


Krok 35.3: Włączenie walidacji w Kontrolerze
--------------------------------------------

Teraz musimy powiedzieć Springowi: "Zanim wpuścisz tego JSON-a do metody, sprawdź, czy spełnia reguły z klasy Transaction". 
Służy do tego adnotacja @Valid.
Otwórz `TransactionController.groovy` i zmodyfikuj sygnaturę metody addTransaction 
(pamiętaj o dodaniu importu import jakarta.validation.Valid na górze!):

Krok 35.4: Obsługa błędu walidacji w GlobalExceptionHandler
-----------------------------------------------------------

Jeśli walidacja nie przejdzie, Spring rzuci wyjątek `MethodArgumentNotValidException`. 
Chcemy go złapać i ładnie sformatować do naszego obiektu `ApiError`.
Otwórz `GlobalExceptionHandler.groovy` i dopisz trzecią metodę (na samym dole klasy):


Krok 35.5: Test Spocka
----------------------

Napiszmy test, który spróbuje "zhackować" Twoje API, wysyłając JSON bez kwoty i kategorii.
Otwórz `TransactionControllerSpec.groovy` i dodaj ten test:

```groovy
def "POST /api/transactions powinien odrzucić błędne dane i zwrócić 400 Bad Request"() {
        given: "JSON z brakującymi, wymaganymi polami (brak amount i category)"
        def badPayload =[
            id: "BAD-1",
            currency: "EUR"
            // BRAK KATEGORII!
            // BRAK KWOTY!
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(badPayload)

        when: "wysyłamy błędne żądanie POST"
        def response = mvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))

        // Możesz odkomentować poniższą linię, aby zobaczyć piękny błąd w konsoli:
        // .andDo(print())

        then: "odpowiedź to 400 Bad Request"
        response.andExpect(status().isBadRequest())

        and: "JSON z błędem zawiera szczegóły, czego brakuje"
        response.andExpect(jsonPath('$.status').value(400))
        response.andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("amount: Kwota (amount) jest wymagana")))
        response.andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("category: Kategoria jest wymagana")))
    }
```

Co zrobiliśmy?
Jeśli kiedykolwiek pisałeś ręczne if-y: if (dto.category == null) throw ..., to właśnie nauczyłeś się, jak tego unikać. 
Adnotacje włączają deklaratywną walidację, która odrzuca złe żądania u samych bram aplikacji (nawet nie wchodząc do środka kontrolera!). Zabezpieczyliśmy system.
Wykonaj kroki 1-5 i odpal testy (./gradlew clean test). 
Zobacz, jak elegancko aplikacja broni się przed zepsutymi danymi!