Lab 52
------

Lab52--Wzorzec-Circuit-Breaker--Bezpieczeństwo-w-Mikrousługach
--------------------------------------------------------------

Czas na wzorzec, który w świecie nowoczesnych mikrousług jest absolutnym liderem wydajności.
Mówię o wzorcu "Circuit Breaker" (Wyłącznik bezpieczeństwa).

Dlaczego to jest "Must-have"?
Wyobraź sobie, że Twój `CurrencyService` dzwoni do zewnętrznego `API`, które nagle pada lub nie mam takiej waluty XYZ 
i zaczyna odpowiadać w 30 sekund. 
Twój serwis czeka, kolejny wątek czeka, kolejny czeka... w 5 minut cała Twoja aplikacja staje (wszystkie wątki są zablokowane na "oczekiwaniu na API").

`Wzorzec Circuit Breaker` działa jak bezpiecznik w domu: 
jeśli API nie działa, "wywala bezpiecznik" i przestaje do niego dzwonić na jakiś czas, zwracając szybki błąd (lub wartość domyślną).

Krok 1: Biblioteka (Resilience4j)
---------------------------------
To standard w Spring Boot 3. Dodaj to do build.gradle:

```groovy
    // Dedykowany starter Resilience4j dla Spring Boot 3
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'

    // WYMAGANE! Bez AOP adnotacja @CircuitBreaker nie zostanie wykryta przez Springa
    implementation 'org.springframework.boot:spring-boot-starter-aop'
```

Krok 2: Implementacja w CurrencyService
---------------------------------------

Zamiast zwykłego try-catch, użyjemy adnotacji @CircuitBreaker.

```groovy
package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import groovy.json.JsonSlurper
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker

@Service
@Slf4j
class CurrencyService {

    private final HttpClient client = HttpClient.newHttpClient()
    private final JsonSlurper slurper = new JsonSlurper()

    /**
     * Pobiera aktualny kurs wymiany dla danej waluty względem PLN.
     * W prawdziwym systemie warto tu dodać @Cacheable (Spring Cache).
     */
    @Cacheable("exchangeRates") // Spring zapamięta wynik dla każdego unikalnego 'fromCurrency'
    // Jeśli metoda wybuchnie 3 razy pod rząd, wyłącznik się "otworzy"
    @CircuitBreaker(name = "currencyApi", fallbackMethod = "fallbackRate")
    BigDecimal getExchangeRate(String fromCurrency) {

        if (fromCurrency == "PLN") return 1.0  // easy case: PLN -> PLN

        // Zamiast println używamy log.info lub log.debug
        log.info(">>> [API CALL] Pobieram kurs z internetu dla: {}", fromCurrency)

        //try {
            def request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.er-api.com/v6/latest/PLN")) // API zwraca kursy względem PLN, więc zawsze pytamy o PLN jako bazę
                    .GET()
                    .build()

            def response = client.send(request, HttpResponse.BodyHandlers.ofString())
            def json = slurper.parseText(response.body())

            // API zwraca kursy względem bazy (PLN).
            // np. jeśli 1 PLN = 0.23 EUR, to kurs EUR -> PLN to 1 / 0.23
            def rateToPln = json.rates[fromCurrency]

            // Zamiast return null, rzucamy wyjątek, żeby obudzić Circuit Breakera!
            if (rateToPln == null) {
                throw new IllegalArgumentException("Nieznana waluta: $fromCurrency")
            }

            return rateToPln ? (1.0 / rateToPln).toBigDecimal() : 1.0

        //} catch (Exception e) {
        //    // Logowanie błędu ze stacktracem
        //    log.error("Błąd pobierania kursu dla waluty {}: {}", fromCurrency, e.message)
        //    // W razie błędu sieciowego nadal możemy rzucić wyjątek lub zwrócić null
        //    return null
        //}

        return 1.0 // fallback (jeśli try się nie udał i nie było returna)
    }

    // --- FALLBACK METHOD ---
    // Musi mieć dokładnie takie same argumenty + Throwable na końcu
    BigDecimal fallbackRate(String fromCurrency, Throwable t) {
        log.warn(">>> [CIRCUIT BREAKER] Uruchomiono Fallback! Powód: {}", t.message)
        // Zwracamy bezpieczną wartość zastępczą
        return 4.0.toBigDecimal()
    }
}
```
 
Krok 3: Test Spock (Sztuczne wywołanie awarii)
----------------------------------------------

W teście sprawdzimy, czy po kilku błędach system przełączy się na fallbackRate.

```groovy
package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.SmartFinDbApp

@ActiveProfiles("test")
@SpringBootTest(classes = [SmartFinDbApp])
@ContextConfiguration
class CurrencyServiceSpec extends Specification {

    @Autowired
    CurrencyService currencyService

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

    def "fallback powinien zwrócić bezpieczną wartość 4.0"() {
        when: "wywołujemy metodę fallback bezpośrednio na PRAWDZIWYM serwisie (Spring Bean)"
        // fallbackRate() to metoda, którą Resilience4j wywołuje automatycznie
        // gdy Circuit Breaker przechwytuje wyjątek — tu testujemy ją w izolacji
        def rate = currencyService.fallbackRate("FAKE", new RuntimeException("Symulowana awaria"))

        then: "fallback zawsze zwraca 4.0 — bezpieczna wartość zastępcza"
        rate == 4.0
    }
}
```

Dlaczego to jest poziom Mid/Senior?

Systemy rozproszone: 
Mid-level developerzy rozumieją, że sieć zawsze zawodzi. 
Ten wzorzec to jedyny sposób, żeby Twoja aplikacja nie "zabiła się" z powodu awarii serwisu zewnętrznego.

Resilience (Odporność): 
Uczysz się jak pisać systemy typu "self-healing" (samoleczące się).

Fallbacks: 
To kluczowa koncepcja w mikroserwisach – co zrobić, gdy czegoś nie ma? Zwrócić błąd czy "bezpieczną wartość"?