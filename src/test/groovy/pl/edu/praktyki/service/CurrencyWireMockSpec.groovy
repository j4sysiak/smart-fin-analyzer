package pl.edu.praktyki.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import pl.edu.praktyki.SmartFinDbApp
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.*

@SpringBootTest(classes = [SmartFinDbApp])
@ContextConfiguration
@ActiveProfiles("test")
class CurrencyWireMockSpec extends Specification {

    // WireMock uruchamiany programowo — w pełni kontrolujemy cykl życia serwera
    @Shared
    static WireMockServer wireMockServer = new WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort()
    )

    @Autowired
    CurrencyService currencyService

    // Dynamiczne ustawianie URL w konfiguracji Springa na adres WireMock-a
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer.start()
        registry.add("currency.api.url", { -> "http://localhost:${wireMockServer.port()}/latest/PLN" })
    }

    def setup() {
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