package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status



// ZMIANA TUTAJ: Dodajemy @WithMockUser na całą klasę!
// - czyli wszystkie testy będą wykonywane w kontekście zalogowanego użytkownika "test-admin"
// z rolą "ADMIN" i nie korzystamy z SecurityControllerSpec, bo tam testujemy zachowanie dla niezalogowanych użytkowników.
@WithMockUser(username = "test-admin", roles = ["ADMIN"])


// 1. @ActiveProfiles(value = ["local-pg"], inheritProfiles = false) powoduje,
//    że kontekst testowy ładuje profil local-pg i w efekcie użyje ustawień z pliku application-local-pg.properties
//    (albo application-local-pg.yml) zamiast domyślnych.
// 2. Test uruchamia pełny kontekst Springa (MockMvc + repozytoria) i będzie próbował połączyć się
//    z bazą zgodnie z ustawieniami z tego pliku.
//    Komentarz w kodzie słusznie mówi: musisz mieć lokalnego Postgresa uruchomionego — test tego serwera
//    nie uruchomi samodzielnie.
// 3. inheritProfiles = false oznacza, że tylko local-pg jest aktywny (inne profile domyślne nie są łączone).
// 4. @WithMockUser(...) na klasie sprawia, że wszystkie testy wykonują się w kontekście zalogowanego
//    użytkownika test-admin z rolą ADMIN.
// 5. H2 nie będzie działać w tym projekcie (jak napisano), więc albo uruchom lokalny Postgres,
//    albo użyj Testcontainers / wbudowanego Postgresa, jeśli chcesz automatycznie startować DB w testach.
// 6. Sprawdź w application-local-pg.properties URL, username i password oraz czy schemat/bazy zostały przygotowane przed uruchomieniem testów.



@AutoConfigureMockMvc

//KNOW HOW!  (ActiveProfiles = 'tc')
// To nie dziala, żeby użyć Testcontainers - profil 'tc', to BaseIntegrationSpec ustaw warunek na if (1==1)
// wtedy zawsze będzie używał Testcontainers.
// Wtedy ten test będzie działał bez konieczności uruchamiania ręcznie Postgresa na Docker.
// wtedy postgres będzie uruchamiany automatycznie w kontenerze Docker przez Testcontainers,
// a po zakończeniu testów będzie automatycznie zatrzymywany i usuwany.

//@ActiveProfiles(value = ["tc"], inheritProfiles = false)


//KNOW HOW!  (ActiveProfiles = 'local-pg')
// Wymusi użycie application-local-pg.properties ale musisz mieć wlączony lokalny Postgresa!
// (nie działa z H2, bo H2 nie obsługuje funkcji SQL, których używamy w repozytorium)
// tutaj info jak uruchomić lokalnego postgresa na dokerze dla profilu: local-pg:
//                     C:\dev\smart-fin-analyzer\src\test\resources\application-local-pg.properties

@ActiveProfiles("tc") // use Testcontainers for tests (start PostgreSQL container automatically)
class TransactionControllerSpec extends BaseIntegrationSpec {

    @Autowired
    MockMvc mvc

    @Autowired
    TransactionRepository repository

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
        repository.save(new TransactionEntity(
                originalId: "T1", amountPLN: 100.0, category: "Test", description: "Wpływ"
        ))
        repository.save(new TransactionEntity(
                originalId: "T2", amountPLN: -20.0, category: "Jedzenie", description: "Pizza"
        ))
    }

    def "GET /api/transactions powinien zwrócić Page<Transaction> w formacie JSON"() {
        expect: "zapytanie zwraca status 200 OK i Page z polem content zawierającym listę"
        mvc.perform(get("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
        // Kontroler zwraca Page<Transaction> - lista jest w polu `content`
                .andExpect(jsonPath('$.content.length()').value(2)) // Sprawdzamy rozmiar listy
                // Nie polegamy na dokładnym porządku elementów w liście - szukamy elementów po unikalnym polu originalId
                .andExpect(jsonPath("\$.content[?(@.id=='T1')].category").value(org.hamcrest.Matchers.contains("Test")))
                .andExpect(jsonPath("\$.content[?(@.id=='T2')].description").value(org.hamcrest.Matchers.contains("Pizza")))
                .andExpect(jsonPath('$.pageable').exists())
    }

    def "GET /api/transactions/stats powinien zwrócić poprawne podsumowanie"() {
        expect: "statystyki są wyliczone poprawnie (100 - 20 = 80)"
        mvc.perform(get("/api/transactions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.balance').value(80.0))
                .andExpect(jsonPath('$.count').value(2))
    }

    def "GET /api/transactions/{id} powinien zwrócić 404 w ustandaryzowanym formacie ApiError"() {
        expect: "próba pobrania rekordu 9999 kończy się ustandaryzowanym błędem"
        mvc.perform(get("/api/transactions/9999"))
                .andDo(print()) // <--- TA LINIJKA WYDRUKUJE WSZYSTKO NA EKRAN!
                .andExpect(status().isNotFound())
        // Sprawdzamy nową strukturę z klasy ApiError:
                .andExpect(jsonPath('$.status').value(404))
                .andExpect(jsonPath('$.message').value("Transakcja o ID 9999 nie istnieje"))
                .andExpect(jsonPath('$.timestamp').exists())
    }

    def "POST /api/transactions powinien zapisać transakcję, przeliczyć walutę i nadać tagi"() {
        given: "nowa transakcja w formacie JSON zbudowana za pomocą mapy Groovy"
        // Zobacz jak czysto to wygląda! Żadnych klas, po prostu definicja danych.
        def newTxPayload =[
                id: "NEW-1",
                amount: -50,
                currency: "USD",
                category: "Gry",
                description: "Zakup na Steam"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(newTxPayload)

        when: "wysyłamy żądanie POST"
        def response = mvc.perform(post("/api/transactions") // to trafi do TransactionController.addTransaction()
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))

        then: "status HTTP to 201 Created"
        response.andExpect(status().isCreated())

        and: "waluta została przeliczona i zwrócona w odpowiedzi"
        // amountPLN nie wysyłaliśmy! API samo to policzyło. Sprawdzamy czy istnieje (nie jest null)
        response.andExpect(jsonPath('$.amountPLN').exists())

        and: "transakcja otrzymała tag (ponieważ -50 USD to więcej niż -100 PLN)"
        response.andExpect(jsonPath('$.tags[0]').value("BIG_SPENDER"))

        and: "baza danych powiększyła się o nowy rekord"
        repository.findAll().size() == 3 // 2 z metody setup() + 1 nowy
    }

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
        def response = mvc.perform(post("/api/transactions")  // to trafi do TransactionController.addTransaction()
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))

        // Możesz odkomentować poniższą linię, aby zobaczyć piękny błąd w konsoli:
         .andDo(print())

        then: "odpowiedź to 400 Bad Request"
        response.andExpect(status().isBadRequest())

        and: "JSON z błędem zawiera szczegóły, czego brakuje"
        response.andExpect(jsonPath('$.status').value(400))
        response.andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("amount: Kwota (amount) jest wymagana")))
        response.andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("category: Kategoria jest wymagana")))
    }
}