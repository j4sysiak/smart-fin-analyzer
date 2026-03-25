package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.repository.TransactionRepository

import java.time.LocalDate


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

@ActiveProfiles(value = ["local-pg"], inheritProfiles = false) // pamietaj, że musisz mieć lokalnego Postgresa uruchomionego, żeby ten test działał!
class SmartFinIntegrationSpec extends BaseIntegrationSpec {

    @Autowired
    TransactionIngesterService pipelineService

    @Autowired
    TransactionRepository repository

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
    }

    def "powinien zaimportować transakcje wielowątkowo i natychmiast oznaczyć je dynamicznymi tagami"() {
        given: "dwie paczki transakcji (np. z dwóch różnych banków)"
        def bankA = [
                new Transaction(id: "A1", date: LocalDate.now(), amount: 5000.0, category: "Praca", description: "Wypłata"),
                new Transaction(id: "A2", date: LocalDate.now(), amount: -15.0, category: "Jedzenie", description: "Kawa")
        ]

        def bankB = [
                new Transaction(id: "B1", date: LocalDate.now(), amount: -2500.0, category: "Dom", description: "Czynsz"),
                new Transaction(id: "B2", date: LocalDate.now(), amount: -45.0, category: "Rozrywka", description: "Netflix")
        ]

        and: "zestaw reguł biznesowych zdefiniowanych przez użytkownika"
        def myRules = [
                "if (amount > 0) addTag('INCOME')",
                "if (amount < -1000) addTag('HIGH_EXPENSE')",
                "if (description.contains('Netflix')) addTag('SUBSCRIPTION')"
        ]

        when: "uruchamiamy główny rurociąg przetwarzający dane równolegle"
        // Przekazujemy listę list (bankA, bankB) oraz nasze reguły
        def processedData = pipelineService.ingestAndApplyRules([bankA, bankB], myRules)

        then: "mamy wszystkie 4 transakcje w jednej płaskiej liście"
        processedData.size() == 4

        and: "Wypłata została rozpoznana jako przychód"
        def incomeTx = processedData.find { it.id == "A1" }
        incomeTx.tags.contains("INCOME")

        and: "Czynsz został oznaczony jako wysoki wydatek"
        def rentTx = processedData.find { it.id == "B1" }
        rentTx.tags.contains("HIGH_EXPENSE")

        and: "Netflix został rozpoznany jako subskrypcja"
        def netflixTx = processedData.find { it.id == "B2" }
        netflixTx.tags.contains("SUBSCRIPTION")

        and: "Kawa nie dostała żadnego tagu (żadna reguła nie pasuje)"
        def coffeeTx = processedData.find { it.id == "A2" }
        coffeeTx.tags.isEmpty()
    }
}