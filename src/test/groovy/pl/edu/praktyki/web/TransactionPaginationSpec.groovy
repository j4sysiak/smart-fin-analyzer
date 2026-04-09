package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.facade.TransactionBulkSaver
import pl.edu.praktyki.security.JwtService

// Twój nowy serwis!
import java.time.LocalDate

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


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
class TransactionPaginationSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired TransactionRepository repo
    @Autowired TransactionBulkSaver bulkSaver

    // Wstrzykujemy JwtService, żeby wygenerować token w teście
    @Autowired JwtService jwtService

    def setup() {
        repo.deleteAll()  // ważne, żeby zacząć z pustą bazą danych przed każdym testem

        // Generujemy 15 encji testowych i używamy TWOJEGO pięknego zoptymalizowanego bulk savera do hurtowego zapisu!
        def testData = (1..15).collect { i ->
            new TransactionEntity(
                    originalId: "TX-$i",
                    amount: i * 100.0, // Kwoty: 100, 200, 300... 1500
                    category: "Paginacja",
                    date: LocalDate.now().minusDays(i)
            )
        }
        bulkSaver.saveAllInTransaction(testData)
    }

    def "powinien poprawnie podzielić dane na strony i posortować"() {
        expect: "Żądamy strony 0, rozmiar 5, sortowanie malejące po 'amount'"
        String token = jwtService.generateToken("admin") // <-- KLUCZOWE JWT
        // Ten test żąda tylko jednej strony (strona 0) o rozmiarze 5,
        // więc odpowiedź zawiera 5 rekordów: (indeksy $.content[0]..$.content[4]).
        // Metadane ($.totalElements = 15, $.totalPages = 3)
        // mówią, że w sumie są 3 strony,
        // ale test weryfikuje tylko pierwszą stronę i jej sortowanie
        // (największe kwoty: 1500, 1400, 1300, 1200, 1100).
        // Jeśli chcesz inną stronę — zmień parametr page.
        // Jeśli chcesz 10 rekordów na stronie to zmień parametr na .param("size", "10").
        // Uwaga: serwer może narzucać limit (w Twoich testach jest ochrona, która przytnie wartość do 100).

        // To żądanie testowe robi następujące rzeczy:
        // 1. Wysyła GET na endpoint `/api/transactions` z nagłówkiem Authorization:
        //    Bearer <token> (token generowany wcześniej przez jwtService.generateToken("admin"))
        //    i Content-Type: application\/json.
        // 2. Przekazuje parametry zapytania: page=0, size=5, sortBy=amount, direction=DESC.
        // 3. Sprawdza, że odpowiedź ma status HTTP 200 OK.
        // 4. Weryfikuje metadane paginacji w JSONie:
        //    $.totalElements ma wartość 15,
        //    $.totalPages ma 3,
        //    $.size ma 5,
        //    $.number ma 0.
        // 5. Weryfikuje sortowanie wyników:
        //    $.content[0].amount ma 1500.0,
        //    $.content[4].amount ma 1100.0 — to odpowiada danym testowym (kwoty 100,200,...,1500) posortowanym malejąco.

        mvc.perform(get("/api/transactions")   // to trafi do TransactionController.getAll()
                .header("Authorization", "Bearer $token") // <-- KLUCZOWE JWT
                .contentType(MediaType.APPLICATION_JSON)
                .param("page", "0")  // Jeśli chcesz inną stronę — zmień parametr page.
                .param("size", "5")  //  Jeśli chcesz 10 rekordów na stronie to zmień parametr na .param("size", "10")
                .param("sortBy", "amount")
                .param("direction", "DESC"))
                .andExpect(status().isOk())

                // Weryfikacja metadanych Paginacji
                .andExpect(jsonPath('$.totalElements').value(15))  // to jest całkowita liczba rekordów w bazie (15 testowych encji)
                .andExpect(jsonPath('$.totalPages').value(3)) // 15 rekordów / 5 rekorow-na-stronie = 3 strony (0, 1, 2)
                .andExpect(jsonPath('$.size').value(5))  // to jest rozmiar strony, który zażądaliśmy (5 rekordów na stronę)
                .andExpect(jsonPath('$.number').value(0)) // Jesteśmy na stronie 0

                // Weryfikacja sortowania (najwyższe kwoty powinny być pierwsze)
                .andExpect(jsonPath('$.content[0].amount').value(1500.0))
                .andExpect(jsonPath('$.content[4].amount').value(1100.0))
    }

    def "powinien zablokować próbę pobrania zbyt dużej strony (OOM Protection)"() {
        expect: "Żądamy miliona rekordów naraz"
        String token = jwtService.generateToken("admin") // <-- KLUCZOWE JWT
        mvc.perform(get("/api/transactions")
                .header("Authorization", "Bearer $token") // <-- KLUCZOWE JWT
                .param("size", "1000000"))
                .andExpect(status().isOk())
                // Nasz bezpiecznik (Math.min) powinien obciąć to do 100
                .andExpect(jsonPath('$.size').value(100))
    }
}