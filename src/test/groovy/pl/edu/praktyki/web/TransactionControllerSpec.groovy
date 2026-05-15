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
import pl.edu.praktyki.repository.CategoryRepository
import pl.edu.praktyki.repository.CategoryEntity

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf




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

    @Autowired
    CategoryRepository categoryRepository

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
        categoryRepository.deleteAll()

        def catTest = categoryRepository.save(new CategoryEntity(name: "Test", monthlyLimit: 1000.0))
        def catJedzenie = categoryRepository.save(new CategoryEntity(name: "Jedzenie", monthlyLimit: 1000.0))
        def catGry = categoryRepository.save(new CategoryEntity(name: "Gry", monthlyLimit: 1000.0))

        repository.save(new TransactionEntity(
                originalId: "T1", amountPLN: 100.0, categoryEntity: catTest, category: catTest.name, description: "Wpływ", ownerUsername: "test-admin"
        ))
        repository.save(new TransactionEntity(
                originalId: "T2", amountPLN: -20.0, categoryEntity: catJedzenie, category: catJedzenie.name, description: "Pizza", ownerUsername: "test-admin"
        ))
        repository.save(new TransactionEntity(
                originalId: "T3", amountPLN: -200.0, categoryEntity: catGry, category: catGry.name, description: "Quake", ownerUsername: "test-admin"
        ))
    }

    def "GET /api/transactions powinien zwrócić Page<Transaction> w formacie JSON"() {
        expect: "zapytanie zwraca status 200 OK i Page z polem content zawierającym listę"
        mvc.perform(get("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Kontroler zwraca Page<Transaction> - lista jest w polu `content`
                .andExpect(jsonPath('$.content.length()').value(3)) // Sprawdzamy rozmiar listy
                // Nie polegamy na dokładnym porządku elementów w liście - szukamy elementów po unikalnym polu originalId
                .andExpect(jsonPath("\$.content[?(@.id=='T1')].category").value(org.hamcrest.Matchers.contains("Test")))
                .andExpect(jsonPath("\$.content[?(@.id=='T2')].description").value(org.hamcrest.Matchers.contains("Pizza")))
                .andExpect(jsonPath('$.pageable').exists())
    }

    def "GET /api/transactions/stats powinien zwrócić poprawne podsumowanie"() {
        expect: "statystyki są wyliczone poprawnie (100 - 20 - 200 = -120)"
        mvc.perform(get("/api/transactions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.balance').value(-120.0))
                .andExpect(jsonPath('$.count').value(3))
    }

    def "GET /api/transactions/{id} powinien zwrócić 404 w ustandaryzowanym formacie ApiError"() {
        expect: "próba pobrania rekordu 9999 kończy się ustandaryzowanym błędem"
        mvc.perform(get("/api/transactions/9999"))
                .andDo(print()) // <--- TA LINIJKA WYDRUKUJE WSZYSTKO NA EKRAN!
                .andExpect(status().isNotFound())
                // Sprawdzamy nową strukturę z klasy ApiError:
                .andExpect(jsonPath('$.status').value(404))
                .andExpect(jsonPath('$.message').value("Transakcja nie istnieje lub brak uprawnień"))
                .andExpect(jsonPath('$.timestamp').exists())
    }

    def "POST /api/transactions powinien utworzyć nowa transakcje"() {
        given: "poprawny payload JSON"
        def payload = [
                id         : "NEW-POST-1",
                amount     : -50,
                currency   : "USD",
                category   : "Gry",
                description: "Zakup Steam"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(payload)

        when: "wysylane jest zadanie POST"
        def response = mvc.perform(post("/api/transactions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andDo(print())

        then: "endpoint zwraca 201 Created"
        response.andExpect(status().isCreated())

        and: "odpowiedz zawiera dane utworzonej transakcji"
        response.andExpect(jsonPath('$.id').value("NEW-POST-1"))
        response.andExpect(jsonPath('$.category').value("Gry"))
        response.andExpect(jsonPath('$.description').value("Zakup Steam"))
        response.andExpect(jsonPath('$.amountPLN').exists())

        and: "rekord zostal zapisany w bazie"
        repository.findAll().size() == 4
        repository.findAll().any { it.originalId == "NEW-POST-1" }
    }

    def "POST /api/transactions powinien utworzyć nowa transakcje z powiązana kategoria"() {
        given: "poprawny payload JSON"
        def payload = [
                id         : "NEW-POST-1",
                amount     : -50,
                currency   : "USD",
                category   : "Gry2",
                description: "Zakup Steam"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(payload)

        when: "wysylane jest zadanie POST"
        def response = mvc.perform(post("/api/transactions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andDo(print())

        then: "endpoint zwraca 201 Created"
        response.andExpect(status().isCreated())

        and: "odpowiedz zawiera dane utworzonej transakcji"
        response.andExpect(jsonPath('$.id').value("NEW-POST-1"))
        response.andExpect(jsonPath('$.category').value("Gry2"))
        response.andExpect(jsonPath('$.description').value("Zakup Steam"))
        response.andExpect(jsonPath('$.amountPLN').exists())

        and: "rekord zostal zapisany w bazie"
        def saved = repository.findAll().find { it.originalId == "NEW-POST-1" }
        saved != null

        and: "transakcja ma ustawione powiazanie do kategorii"
        saved.category == "Gry2"
        saved.categoryEntity != null
        saved.categoryEntity.name == "Gry2"

        and: "kategoria istnieje w bazie"
        categoryRepository.findAll().any { it.name == "Gry2" }
    }

    def "POST /api/transactions powinien utworzyć nową kategorię i ustawić category_id"() {
        given: "payload z kategorią, której nie ma jeszcze w bazie"
        def payload = [
                id         : "NEW-CAT-1",
                amount     : -25,
                currency   : "PLN",
                category   : "NowaKategoria",
                description: "Test nowej kategorii"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(payload)

        when: "wysyłane jest żądanie POST"
        def response = mvc.perform(post("/api/transactions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))

        then: "API tworzy transakcję"
        response.andExpect(status().isCreated())
        response.andExpect(jsonPath('$.category').value("NowaKategoria"))

        and: "w bazie istnieje nowa kategoria"
        def createdCategory = categoryRepository.findByName("NowaKategoria").orElse(null)
        createdCategory != null

        and: "transakcja ma relację categoryEntity i ustawione category_id"
        def saved = repository.findAll().find { it.originalId == "NEW-CAT-1" }
        saved != null
        saved.categoryEntity != null
        saved.categoryEntity.id != null
        saved.categoryEntity.name == "NowaKategoria"
    }

    def "POST /api/transactions powinien trimowac kategorie i ustawic category_id"() {
        given: "payload z kategorią zawierającą białe znaki"
        def payload = [
                id         : "NEW-CAT-TRIM-1",
                amount     : -35,
                currency   : "PLN",
                category   : "  Gry  ",
                description: "Test trim kategorii"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(payload)

        when: "wysyłane jest żądanie POST"
        def response = mvc.perform(post("/api/transactions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))

        then: "API zwraca kategorię po normalizacji"
        response.andExpect(status().isCreated())
        response.andExpect(jsonPath('$.category').value("Gry"))

        and: "transakcja ma poprawnie ustawioną relację do istniejącej kategorii"
        def saved = repository.findAll().find { it.originalId == "NEW-CAT-TRIM-1" }
        saved != null
        saved.category == "Gry"
        saved.categoryEntity != null
        saved.categoryEntity.id != null
        saved.categoryEntity.name == "Gry"

        and: "nie tworzymy duplikatu kategorii"
        categoryRepository.findAll().count { it.name == "Gry" } == 1
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
                .andDo(print())  // aby zobaczyć piękny błąd w konsoli (jak będzie):

        then: "status HTTP to 201 Created"
        response.andExpect(status().isCreated())

        and: "waluta została przeliczona i zwrócona w odpowiedzi"
        // amountPLN nie wysyłaliśmy! API samo to policzyło. Sprawdzamy czy istnieje (nie jest null)
        response.andExpect(jsonPath('$.amountPLN').exists())

        and: "transakcja otrzymała tag (ponieważ -50 USD to więcej niż -100 PLN)"
        response.andExpect(jsonPath('$.tags[0]').value("BIG_SPENDER"))

        and: "baza danych powiększyła się o nowy rekord"
        repository.findAll().size() == 4 // 3 z metody setup() + 1 nowy
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
                .andDo(print())  // aby zobaczyć piękny błąd w konsoli (jak będzie):

        then: "odpowiedź to 400 Bad Request"
        response.andExpect(status().isBadRequest())

        and: "JSON z błędem zawiera szczegóły, czego brakuje"
        response.andExpect(jsonPath('$.status').value(400))
        response.andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("amount: Kwota (amount) jest wymagana")))
        response.andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("category: Kategoria jest wymagana")))
    }

    def "PUT /api/transactions/{dbId} powinien zaktualizowac wlasna transakcje"() {
        given: "istniejaca transakcja oraz poprawny payload aktualizacji"
        def existing = repository.findAll().find { it.originalId == "T2" }

        def updatePayload = [
                id         : "T2",
                amount     : -25,
                currency   : "PLN",
                category   : "Jedzenie",
                description: "Pizza XXL"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(updatePayload)

        when: "wysylamy zadanie PUT po dbId"
        def response = mvc.perform(put("/api/transactions/${existing.dbId}")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andDo(print())  // aby zobaczyć piękny błąd w konsoli (jak będzie):

        then: "odpowiedz ma status 200 OK"
        response.andExpect(status().isOk())

        and: "zwracana transakcja zawiera zaktualizowane dane"
        response.andExpect(jsonPath('$.id').value("T2"))
        response.andExpect(jsonPath('$.category').value("Jedzenie"))
        response.andExpect(jsonPath('$.description').value("Pizza XXL"))

        and: "zmiany zostaly zapisane w bazie"
        def updated = repository.findById(existing.dbId).orElseThrow()
        updated.description == "Pizza XXL"
        updated.category == "Jedzenie"
        updated.amountPLN == -25.0d
    }

    def "PUT /api/transactions/{dbId} powinien zwrocic 404 dla nieistniejacej transakcji"() {
        given: "poprawny payload, ale nieistniejace dbId"
        def updatePayload = [
                id         : "X-404",
                amount     : -10,
                currency   : "PLN",
                category   : "Test",
                description: "Nie istnieje"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(updatePayload)

        expect: "odpowiedz to 404 Not Found"
        mvc.perform(put("/api/transactions/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.status').value(404))
                .andExpect(jsonPath('$.message').value("Transakcja nie istnieje lub brak uprawnień"))
    }
}