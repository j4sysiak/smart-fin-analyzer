package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import java.net.http.HttpClient
import java.net.http.HttpResponse

@ContextConfiguration(classes = [CurrencyService])
class CurrencyServiceSpec extends Specification {

    @Autowired
    CurrencyService currencyService

    def "powinien pobrać kurs wymiany dla EUR na podstawie odpowiedzi API"() {
        given: "mockujemy klienta HTTP zwracającego dane z API"
        def mockJson = '{"result":"success","rates":{"EUR":0.23}}'
        HttpResponse<String> mockResponse = Stub(HttpResponse) { body() >> mockJson }
        HttpClient mockClient = Mock(HttpClient) { send(_, _) >> mockResponse }
        def service = new CurrencyService(mockClient)

        when: "pytamy o kurs EUR"
        def rate = service.getExchangeRate("EUR")

        then: "kurs powinien być większy niż 4.0 (1/0.23 ≈ 4.35)"
        rate > 4.0
        rate instanceof BigDecimal
    }

    def "powinien zwrócić 1.0 dla PLN"() {
        expect:
        currencyService.getExchangeRate("PLN") == 1.0
    }

    def "powinien przeliczyć kwotę na PLN"() {
        given: "mockujemy klienta HTTP dla kursu EUR"
        def mockJson = '{"result":"success","rates":{"EUR":0.23}}'
        HttpResponse<String> mockResponse = Stub(HttpResponse) { body() >> mockJson }
        HttpClient mockClient = Mock(HttpClient) { send(_, _) >> mockResponse }
        def service = new CurrencyService(mockClient)

        when: "przeliczamy 100 EUR na PLN"
        def amountPLN = service.convertToPLN(100.0G, "EUR")

        then: "kwota powinna wynosić ponad 400 PLN"
        amountPLN > 400.0
    }

    def "powinien przeliczyć kwotę PLN bez zmian"() {
        expect:
        currencyService.convertToPLN(250.0G, "PLN") == 250.0G
    }
}