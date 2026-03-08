package pl.edu.praktyki.strategy

import pl.edu.praktyki.service.OrderProcessor
import spock.lang.Specification

class OrderProcessorSpec extends Specification {

    def orderProcessor = new OrderProcessor(strategies: [new VipOrderStrategy(), new StandardOrderStrategy()])

    def "powinien automatycznie wykryć i użyć strategii VIP"() {
        when: "procesujemy zamówienie typu VIP"
        orderProcessor.process("VIP", 100.0)

        then: "nie ma błędu i strategia została wywołana"
        noExceptionThrown()
    }

    def "powinien rzucić błąd dla nieznanego typu"() {
        when: "podajemy typ, którego nie mamy w kodzie"
        orderProcessor.process("UNKNOWN", 100.0)

        then: "dostajemy wyjątek"
        thrown(IllegalArgumentException)
    }
}