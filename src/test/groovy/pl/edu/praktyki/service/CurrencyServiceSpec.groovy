package pl.edu.praktyki.service

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionRepository
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.SmartFinDbApp

// @ActiveProfiles("test")
// @SpringBootTest(classes = [SmartFinDbApp])
// @ContextConfiguration

@AutoConfigureMockMvc
@ActiveProfiles(value = "tc", inheritProfiles = false)
class CurrencyServiceSpec extends BaseIntegrationSpec { // <-- DZIEDZICZYMY!

    @Autowired
    TransactionRepository repository

    @Autowired
    CurrencyService currencyService

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
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

    def "fallback powinien zwrócić bezpieczną wartość 4.0"() {
        when: "wywołujemy metodę fallback bezpośrednio na PRAWDZIWYM serwisie (Spring Bean)"
        // fallbackRate() to metoda, którą Resilience4j wywołuje automatycznie
        // gdy Circuit Breaker przechwytuje wyjątek — tu testujemy ją w izolacji
        def rate = currencyService.fallbackRate("FAKE", new RuntimeException("Symulowana awaria"))

        then: "fallback zawsze zwraca 4.0 — bezpieczna wartość zastępcza"
        rate == 4.0
    }
}