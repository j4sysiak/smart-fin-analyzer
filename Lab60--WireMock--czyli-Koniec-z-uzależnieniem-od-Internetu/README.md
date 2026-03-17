Lab 60
------

Lab60--WireMock--czyli-Koniec-z-uzależnieniem-od-Internetu
----------------------------------------------------------


Obecnie Twój CurrencyService łączy się z `https://open.er-api.com` - 
              Pobiera aktualny kurs wymiany dla danej waluty względem PLN

Problem: 
Jeśli to API padnie, Twoje testy (`CurrencyServiceSpec`, `SmartFinIntegrationSpec`) zaczną świecić na czerwono. 
W firmie oznacza to, że nikt nie może wdrożyć kodu (zablokowany CI/CD Pipeline), bo serwer zewnętrzny ma awarię. 
To niedopuszczalne!

Rozwiązanie: `WireMock`. 
To `wirtualny serwer`, który `Spring Boot` stawia na czas testów, a my mówimy mu: 
"Kiedy ktoś zapyta Cię o kurs walut, udawaj prawdziwe API i zwróć ten JSON".

Krok 1: Dodanie zależności do build.gradle
------------------------------------------

Otwórz plik build.gradle i w sekcji dependencies dodaj WireMocka:

```groovy
// WireMock do symulowania zewnętrznych serwerów REST API
testImplementation 'org.wiremock:wiremock-standalone:3.3.1'
testImplementation 'org.springframework.cloud:spring-cloud-contract-wiremock:4.1.0'
```
(Odśwież Gradle - Sloń)

Krok 2: Przygotowanie serwisu na zmianę adresu URL
--------------------------------------------------

Zły kod (tzw. Hardcoded): 
Twój `CurrencyService` ma na sztywno wpisany adres https://open.er-api.com....

Dobry kod (Mid-level): 
Adres URL powinien być wczytywany z pliku `application.properties`, żeby w testach można było go podmienić na adres `WireMocka` (czyli na localhost).

Zmień `CurrencyService.groovy`, aby wyglądał tak:

```groovy
import org.springframework.beans.factory.annotation.Value // DODAJ IMPORT
// ...
class CurrencyService {
// ...

    // Zaciągamy URL z konfiguracji Springa
    @Value('${currency.api.url:https://open.er-api.com/v6/latest/PLN}')
    private String apiUrl

    @Cacheable("exchangeRates")
    @CircuitBreaker(name = "currencyApi", fallbackMethod = "fallbackRate")
    BigDecimal getExchangeRate(String fromCurrency) {
        if (fromCurrency == "PLN") return 1.0

        log.info(">>> [API CALL] Pobieram kurs z internetu dla: {}", fromCurrency)

        // UŻYWAMY ZMIENNEJ ZAMIAST TEKSTU NA SZTYWNO!
        def request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl)) 
                .GET()
                .build()
        
        // ... reszta bez zmian
```

Krok 3: Magia WireMocka w teście Spock (WireMockSpec.groovy)
------------------------------------------------------------

Stworzymy nowy test integracyjny, który pokaże, jak w pełni uniezależnić się od sieci.

Stwórz plik `src/test/groovy/pl/edu/praktyki/service/CurrencyWireMockSpec.groovy`:


```groovy
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
```

UWAGA:
-----
Test PASSED ✅
Podsumowując, problem miał dwie przyczyny:
Konflikt zależności WireMock — na classpath były dwa WireMock-i jednocześnie:
 1. wiremock-standalone:3.3.1
 2. wiremock-jre8-standalone:2.35.1 z spring-cloud-contract-wiremock

Usunięto ręczny wiremock-standalone.

Byl Brak @ContextConfiguration — Spock-Spring 2.3 nie wykrywa samego @SpringBootTest jako triggera integracji ze Springiem. 
Bez dodatkowego @ContextConfiguration, Spock nie aktywuje SpringExtension i pola @Autowired zostają null. 
Dlatego currencyService był null, a WireMock.reset() łączył się z portem 0.
Dodatkowo zmieniono podejście z @AutoConfigureWireMock (które nie działało) 
na programowe uruchomienie WireMockServer z @DynamicPropertySource — daje to pełną kontrolę nad cyklem życia serwera WireMock.


To, co właśnie zrobiłeś, to "The Senior Way" radzenia sobie z testami integracyjnymi.
Zamiast walczyć z "magicznymi" adnotacjami Spring Cloud (które często ukrywają przed Tobą, co się dzieje w środku), 
przejąłeś pełną kontrolę nad cyklem życia WireMocka.
Dlaczego to, co zrobiłeś, jest tak ważne:

@DynamicPropertySource: 
To jest absolutnie najpotężniejsza metoda w Spring Test. 
Pozwala ona dynamicznie wstrzyknąć do aplikacji (w trakcie trwania testu) adres portu, na którym akurat uruchomił się WireMock. 
Dzięki temu nie musisz "zgadywać" portu w pliku properties – masz go "na żywo".

Unikanie "Dependency Hell": 
Wyczyszczenie bibliotek i pozostawienie tylko jednej wersji WireMocka to profesjonalne podejście. 
Konflikty wersji (JAR Hell) to najczęstsza przyczyna "dziwnych" błędów w Javie.

Programowe sterowanie serwerem (WireMockServer): 
Używając klasy WireMockServer zamiast adnotacji, masz 100% pewności, kiedy serwer startuje, kiedy się resetuje i kiedy umiera. 
To sprawia, że testy są deterministyczne (zawsze działają tak samo).


------------------------------------------------------------------------------------------------------------------------------

Dlaczego ta lekcja robi z Ciebie Mida?

Rekruter pyta: "Jak testujesz integrację z systemem zewnętrznym, np. API NBP, którego nie kontrolujesz?".
Jeśli powiesz: "Używam Mockito i mockuję mój obiekt RestTemplate/HttpClient", to znaczy, że nie testujesz deserializacji JSON ani nagłówków HTTP. 
Testujesz tylko swoją Javę.
Kiedy mówisz: "Zarzucam WireMocka, podmieniam URL w application-test.properties i serwuję sztuczny JSON przez port localhosta" – to brzmisz jak inżynier.
Twój kod przechodzi przez prawdziwy stos sieciowy TCP/IP, ale nie opuszcza Twojego komputera.

Wdróż to! Zobacz, jak test staje się 10x szybszy (bo nie czeka na DNS i routing w sieci).

Daj znać, jak odpalisz ten test! (Upewnij się, że WiFi działa, bo Gradle musi ściągnąć WireMocka). 🚀🌐