package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(classes = [CurrencyService])
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
}