package pl.edu.praktyki.event

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.service.AsyncNotificationService
import org.springframework.beans.factory.annotation.Autowired
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit
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

@ActiveProfiles(value = ["local-pg"], inheritProfiles = false) // pamietaj, że musisz mieć lokalnego Postgresa uruchomionego, żeby ten test działał!
class EventDecouplingSpec extends BaseIntegrationSpec {

    @Autowired SmartFinFacade facade
    @Autowired AsyncNotificationService notificationService

    def "powinien zwrócić raport synchronicznie i wysłać powiadomienie asynchronicznie"() {
        given: "transakcja testowa"
        def data = [new Transaction(id: "ASYNC-TEST-1", amount: 500, currency: "PLN", category: "Test", date: LocalDate.now())]

        // Resetujemy licznik przez metodę
        notificationService.reset()

        when: "wywołujemy fasadę"
        String report = facade.processAndGenerateReport("Użytkownik Testowy", data, [])

        then: "1. Raport otrzymujemy natychmiast (synchronicznie)"
        report != null
        report.contains("UŻYTKOWNIK TESTOWY")
        report.contains("500.00 PLN")

        // ZMIANA: Używamy getProcessedCount() zamiast pola
        then: "2. Czekamy, aż asynchroniczny listener skończy pracę"
        await().atMost(10, TimeUnit.SECONDS).until {
            notificationService.getProcessedCount() == 1
        }

        expect: "Wynik końcowy jest poprawny"
        notificationService.getProcessedCount() == 1
    }
}