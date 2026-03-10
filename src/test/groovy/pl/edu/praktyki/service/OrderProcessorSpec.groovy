package pl.edu.praktyki.service

import org.springframework.boot.test.context.SpringBootTest
import pl.edu.praktyki.strategy.OrderStrategy
import pl.edu.praktyki.strategy.VipOrderStrategy
import spock.lang.Specification
import spock.lang.Subject

/*
Test obejmuje:
- **dopasowanie strategii** — sprawdza, czy wywoływana jest właściwa strategia
- **pierwszeństwo** — używa pierwszej pasującej strategii z listy
- **brak strategii** — rzuca `IllegalArgumentException` z odpowiednim komunikatem
- **null jako typ** — edge case dla `null`
*/

class OrderProcessorSpec extends Specification {

    def strategy1 = Mock(OrderStrategy)
    def strategy2 = Mock(OrderStrategy)

    @Subject
    def processor = new OrderProcessor(strategies: [strategy1, strategy2])

    def "should process order with matching strategy"() {
        given:def type = "ONLINE"
        def amount = new BigDecimal("100.00")

        and:
        strategy1.supports(type) >> false
        strategy2.supports(type) >> true

        when:
        processor.process(type, amount)

        then:
        1 * strategy2.process(amount)
        0 * strategy1.process(_)
    }

    def "should use first matching strategy"() {
        given:
        def type = "STORE"
        def amount = new BigDecimal("50.00")

        and:
        strategy1.supports(type) >> true

        when:
        processor.process(type, amount)

        then:
        1 * strategy1.process(amount)
        0 * strategy2.process(_)
    }

    def "should throw exception when no strategy matches"() {
        given:
        def type = "UNKNOWN"
        def amount = new BigDecimal("200.00")

        and:
        strategy1.supports(type) >> false
        strategy2.supports(type) >> false

        when:
        processor.process(type, amount)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Nieznany typ zamówienia: UNKNOWN"
    }

    def "should throw exception when type is null and no strategy supports it"() {
        given:
        strategy1.supports(null) >> false
        strategy2.supports(null) >> false

        when:
        processor.process(null, new BigDecimal("10.00"))

        then:
        thrown(IllegalArgumentException)
    }
}