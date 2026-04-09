package pl.edu.praktyki.web

import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.security.JwtService

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit


// 1. @ActiveProfiles(value = ["local-pg"], inheritProfiles = false) powoduje,
//    że kontekst testowy ładuje profil local-pg i w efekcie użyje ustawień z pliku application-local-pg.properties
//    (albo application-local-pg.yml) zamiast domyślnych.
// 2. Test uruchamia pełny kontekst Springa (MockMvc + repozytoria) i będzie próbował połączyć się
//    z bazą zgodnie z ustawieniami z tego pliku.
//    Komentarz w kodzie słusznie mówi: musisz mieć lokalnego Postgresa uruchomionego — test tego serwera
//    nie uruchomi samodzielnie.
// 3. inheritProfiles = false oznacza, że tylko local-pg jest aktywny (inne profile domyślne nie są łączone).
// 4. H2 nie będzie działać w tym projekcie (jak napisano), więc albo uruchom lokalny Postgres,
//    albo użyj Testcontainers / wbudowanego Postgresa, jeśli chcesz automatycznie startować DB w testach.
// 5. Sprawdź w application-local-pg.properties URL, username i password oraz czy schemat/bazy zostały przygotowane przed uruchomieniem testów.


@AutoConfigureMockMvc

//KNOW HOW!  (ActiveProfiles = 'tc')
// To nie dziala, żeby użyć Testcontainers - profil 'tc', to BaseIntegrationSpec ustaw warunek na if (1==1) linia 40
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
class AsyncBulkSpec extends BaseIntegrationSpec {

    // Wstrzykujemy JwtService, żeby wygenerować token w teście
    @Autowired JwtService jwtService

    @Autowired MockMvc mvc

    @Autowired TransactionRepository repository

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
    }

    def "powinien przyjąć wielką paczkę danych i przetworzyć ją w tle"() {
        given: "1000 transakcji"
        String token = jwtService.generateToken("admin") // <-- KLUCZOWE JWT
        def data = (1..1000).collect { new Transaction(id: "ASYNC-$it", amount: 10.0, category: "Async") }
        String json = groovy.json.JsonOutput.toJson(data)

        when: "uderzamy w endpoint /bulk"
        def response = mvc.perform(post("/api/transactions/bulk")  // leci do TransactionController.bulkUpload()
                .header("Authorization", "Bearer $token") // <-- KLUCZOWE JWT
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))

        then: "serwer odpowiada 202 Accepted natychmiast"
        response.andExpect(status().isAccepted())

        and: "w tej milisekundzie baza wciąż może być pusta"
        repository.count() < 1000

        then: "po krótkiej chwili Awaitility potwierdza, że dane wpadły do bazy"
        await().atMost(5, TimeUnit.SECONDS).until {
            repository.count() == 1000
        }
    }
}