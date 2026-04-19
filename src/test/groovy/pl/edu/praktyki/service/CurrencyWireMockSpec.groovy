package pl.edu.praktyki.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionRepository
import spock.lang.Shared

import static com.github.tomakehurst.wiremock.client.WireMock.*


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

@ActiveProfiles("tc") // use Testcontainers for tests (start PostgreSQL container automatically)
@org.springframework.transaction.annotation.Transactional
@org.springframework.test.annotation.Rollback
class CurrencyWireMockSpec extends BaseIntegrationSpec {

    // WireMock uruchamiany programowo — w pełni kontrolujemy cykl życia serwera
    @Shared
    static WireMockServer wireMockServer = new WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort()
    )

    @Autowired
    CurrencyService currencyService

    @Autowired
    TransactionRepository repository

    // Dynamiczne ustawianie URL w konfiguracji Springa na adres WireMock-a
    // UWAGA: nazwa metody MUSI być inna niż w BaseIntegrationSpec (configureTestcontainers),
    // aby oba @DynamicPropertySource były wykrywane przez Spring.
    @DynamicPropertySource
    static void configureWireMock(DynamicPropertyRegistry registry) {
        wireMockServer.start()
        registry.add("currency.api.url", { -> "http://localhost:${wireMockServer.port()}/latest/PLN" })
    }

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()

        // Czyszczenie stubów przed każdym testem
        wireMockServer.resetAll()
    }

    def cleanupSpec() {
        wireMockServer.stop()
    }

    def "powinien poprawnie sparsować kurs walut z zamockowanego API"() {
        given: "Wirtualny serwer udaje API i zwraca przygotowany przez nas JSON"
        // To jest właśnie definicja STUBA
        wireMockServer.stubFor(get(urlEqualTo("/latest/PLN"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("""
                            {
                              "rates": {
                                "EUR": 0.25,
                                "USD": 0.20
                              }
                            }
                        """)))

        when: "wywołujemy nasz serwis (który myśli, że gada z internetem)"
        def eurRate = currencyService.getExchangeRate("EUR")
        def usdRate = currencyService.getExchangeRate("USD")

        then: "obliczenia są poprawne (1 / 0.25 = 4.0)"
        eurRate == 4.0

        and: "(1 / 0.20 = 5.0)"
        usdRate == 5.0

        and: "weryfikujemy, czy nasz serwis faktycznie uderzył pod ten konkretny endpoint"
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/latest/PLN")))
    }
}