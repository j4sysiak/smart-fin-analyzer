package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import com.github.tomakehurst.wiremock.WireMockServer
import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionRepository

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
class CurrencyServiceSpec extends BaseIntegrationSpec {

    @Autowired
    TransactionRepository repository

    @Autowired
    CurrencyService currencyService

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
    }

    // --- WireMock dla symulacji zewnętrznego API kursów walut ---
    private static final WireMockServer WIREMOCK

    static {
        WIREMOCK = new WireMockServer(options().dynamicPort())
        WIREMOCK.start()

        // Domyślny stub zwraca sensowny kurs EUR względem PLN (1 PLN = 0.23 EUR)
        WIREMOCK.stubFor(get(urlEqualTo('/v6/latest/PLN'))
                .willReturn(aResponse()
                        .withHeader('Content-Type', 'application/json')
                        .withBody('{' +
                                '"rates": { "EUR": 0.23, "PLN": 1.0 }' +
                                '}')
                        .withStatus(200)))
    }

    @DynamicPropertySource
    static void registerCurrencyApiUrl(DynamicPropertyRegistry registry) {
        // Nadpisujemy property używane przez CurrencyService, aby wskazywała na WireMock
        registry.add('currency.api.url', () -> WIREMOCK.baseUrl() + '/v6/latest/PLN')
    }

    def "powinien pobrać kurs wymiany dla EUR"() {
        when: "pytamy o kurs EUR"
        def rate = currencyService.getExchangeRate("EUR")

        then: "kurs powinien być większy niż 4.0 (realia rynkowe)"
        rate > 4.0
        rate instanceof BigDecimal
    }

    def "powinien zwrócić 1.0 dla PLN"() {
        expect:
        currencyService.getExchangeRate("PLN") == 1.0
    }

    def "powinien użyć fallbacka, gdy API rzuca błędy (Circuit Breaker)"() {
        when: "wywołujemy metodę z nieznaną walutą (sprowokowanie błędu)"
        // Uruchamiamy to kilka razy, by zasymulować obciążenie,
        // chociaż Resilience4j domyślnie odpali fallback już przy pierwszym wyjątku
        def rate = currencyService.getExchangeRate("XYZ")

        then: "powinniśmy otrzymać wartość z fallbacka, bez wyrzucania wyjątku w teście!"
        rate == 4.0
    }

    def "powinien użyć fallbacka, gdy API rzuca błędy (wersja z Mockiem)"() {
        given: "zamockowany CurrencyService — pełna kontrola nad odpowiedziami"
        // Mock() tworzy atrapę obiektu. Żadna prawdziwa logika się nie wykonuje.
        // Operator >>> definiuje SEKWENCJĘ odpowiedzi: kolejne wywołania zwracają kolejne wartości.
        def mockedService = Mock(CurrencyService)

        when: "wywołujemy 4 razy — każde kończy się wyjątkiem (symulacja kolejnych awarii)"
        def errors = 0
        4.times {
            try { mockedService.getExchangeRate("USD") } catch (ignored) { errors++ }
        }

        and: "piąte wywołanie — Circuit Breaker przełączył się na fallback"
        def rate = mockedService.getExchangeRate("USD")

        then: "4 awarie + piąte wywołanie zwróciło wartość z fallbacka (4.0)"
        // WAŻNE: W Spocku weryfikacja interakcji (np. 5 * mock.method()) MUSI być w bloku then:
        // i jednocześnie NADPISUJE stubbing z given: — dlatego łączymy stubbing z weryfikacją:
        5 * mockedService.getExchangeRate("USD") >>> [
                { throw new RuntimeException("Awaria API #1") },
                { throw new RuntimeException("Awaria API #2") },
                { throw new RuntimeException("Awaria API #3") },
                { throw new RuntimeException("Awaria API #4") },
                4.0  // 5. wywołanie — symulacja: Circuit Breaker otworzył się i zwraca fallback
        ]
        errors == 4
        rate == 4.0
    }

    def cleanupSpec() {
        if (WIREMOCK && WIREMOCK.isRunning()) {
            WIREMOCK.stop()
        }
    }

    def "fallback powinien zwrócić bezpieczną wartość 4.0"() {
        when: "wywołujemy metodę fallback bezpośrednio na PRAWDZIWYM serwisie (Spring Bean)"
        // fallbackRate() to metoda, którą Resilience4j wywołuje automatycznie
        // gdy Circuit Breaker przechwytuje wyjątek — tu testujemy ją w izolacji
        def rate = currencyService.fallbackRate("FAKE", new RuntimeException("Symulowana awaria"))

        then: "fallback zawsze zwraca 4.0 — bezpieczna wartość zastępcza"
        rate == 4.0
    }
}