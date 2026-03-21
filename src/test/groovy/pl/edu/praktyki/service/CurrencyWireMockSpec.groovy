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

@AutoConfigureMockMvc
@ActiveProfiles(value = ["tc"], inheritProfiles = false)

// Wymusi użycie application-local-pg.properties ale musisz mieć wlączony lokalny Postgresa!
// (nie działa z H2, bo H2 nie obsługuje funkcji SQL, których używamy w repozytorium)
// @ActiveProfiles(value = ["local-pg"], inheritProfiles = false)
class CurrencyWireMockSpec extends BaseIntegrationSpec { // <-- DZIEDZICZYMY!

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
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
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