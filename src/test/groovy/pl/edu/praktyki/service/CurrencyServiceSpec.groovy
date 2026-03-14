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
}