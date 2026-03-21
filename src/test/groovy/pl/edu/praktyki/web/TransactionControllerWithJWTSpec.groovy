package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.security.JwtService

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@ActiveProfiles(value = ["tc"], inheritProfiles = false)

// Wymusi użycie application-local-pg.properties ale musisz mieć wlączony lokalny Postgresa!
// (nie działa z H2, bo H2 nie obsługuje funkcji SQL, których używamy w repozytorium)
// @ActiveProfiles(value = ["local-pg"], inheritProfiles = false)
class TransactionControllerWithJWTSpec extends BaseIntegrationSpec {

    // Wstrzykujemy JwtService, żeby wygenerować token w teście
    @Autowired JwtService jwtService

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

    def "POST /api/transactions powinien przejść, gdy wyślemy poprawny token JWT"() {
        given: "ważny token JWT dla admina"
        String token = jwtService.generateToken("admin") // <-- KLUCZOWE JWT
        def payload = groovy.json.JsonOutput.toJson([id: "T1", amount: 100, category: "IT"])

        when: "wysyłamy żądanie z nagłówkiem Authorization"
        def response = mvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer $token") // <-- KLUCZOWE JWT
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))

        then: "powinniśmy dostać 201 Created"
        response.andExpect(status().isCreated())
    }

    def "POST /api/transactions powinien zapisać transakcję, przeliczyć walutę i nadać tagi"() {
        given: "nowa transakcja w formacie JSON zbudowana za pomocą mapy Groovy"
        String token = jwtService.generateToken("admin") // <-- KLUCZOWE JWT
        // Zobacz jak czysto to wygląda! Żadnych klas, po prostu definicja danych.
        def newTxPayload =[
                id: "NEW-1",
                amount: -50,
                currency: "USD",
                category: "Gry",
                description: "Zakup na Steam"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(newTxPayload)

        when: "wysyłamy żądanie POST z nagłówkiem Authorization"
        def response = mvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer $token") // <-- KLUCZOWE JWT
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
        String token = jwtService.generateToken("admin") // <-- KLUCZOWE JWT
        def badPayload =[
                id: "BAD-1",
                currency: "EUR"
                // BRAK KATEGORII!
                // BRAK KWOTY!
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(badPayload)

        when: "wysyłamy błędne żądanie POST z nagłówkiem Authorization"
        def response = mvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer $token") // <-- KLUCZOWE JWT
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

    def "POST /api/transactions powinien odrzucić zapytanie ze sfałszowanym tokenem (403 Forbidden)"() {
        given: "prawidłowy token"
        String validToken = jwtService.generateToken("admin") // <-- KLUCZOWE JWT

        and: "sfałszowany token (podmieniamy znak w środku)"
        // Zmieniamy znak na pozycji 10 (indeks 9)
        String fakeToken = validToken.substring(0, 9) + "X" + validToken.substring(10)

        def payload = groovy.json.JsonOutput.toJson([id: "T-HACK", amount: 100, category: "IT"])

        when: "wysyłamy żądanie z sfałszowanym tokenem"
        def response = mvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer $fakeToken") // <-- KLUCZOWE JWT
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))

        then: "dostęp zostaje odrzucony (403 Forbidden)"
        // Spring Security w przypadku niepoprawnego tokena (błąd podpisu)
        // zazwyczaj zwraca 403, bo uznaje próbę manipulacji za atak
        response.andExpect(status().isForbidden())
    }
}