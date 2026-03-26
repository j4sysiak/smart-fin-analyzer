package pl.edu.praktyki.facade

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
class FacadeSpec extends BaseIntegrationSpec {

    // Wstrzykujemy TYLKO Fasadę
    //   - nie interesują nas poszczególne serwisy, repozytoria, ani szczegóły implementacji.
    @Autowired SmartFinFacade smartFinFacade

    @Autowired
    TransactionRepository repository


    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
    }

    def "powinien przetworzyć cały proces biznesowy przez jeden punkt dostępu (Fasada)"() {
        given: "dane wejściowe od użytkownika (np. z CLI lub z REST Controller)"
        def userName = "Testowy Użytkownik"
        def data =[
                new Transaction(id: "F1", amount: 100.0, currency: "PLN", category: "Test", date: LocalDate.now())
        ]
        def rules =["if (amountPLN < -100) addTag('BIG_SPENDER')"]

        when: "wywołujemy GŁÓWNĄ metodę Fasady"
        // Klient (nasz test) nie wie o istnieniu repozytoriów, walut ani reguł.
        // Wywołuje tylko jedną metodę, a Fasada orkiestruje resztę.
        def generatedReport = smartFinFacade.saveTransactionsAndGenerateReport(userName, data, rules)

        then: "cały proces zakończył się sukcesem, zwracając gotowy raport"
        generatedReport != null
        generatedReport.contains("RAPORT FINANSOWY DLA: TESTOWY UŻYTKOWNIK")

        and: "raport zawiera przetworzone dane (np. 100 PLN na plusie)"
        generatedReport.contains("Status: NA PLUSIE")

        // Opcjonalnie: Wydrukuj raport na konsolę, aby zobaczyć efekt pracy Fasady
        println ">>> RAPORT Z FASADY:\n$generatedReport"
    }
}