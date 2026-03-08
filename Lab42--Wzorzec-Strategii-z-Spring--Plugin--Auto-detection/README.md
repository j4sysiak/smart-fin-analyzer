Lab 42
------

Lab 29: Strategia Wyboru Implementacji (Wzorce Projektowe)
----------------------------------------------------------

W Javie często mamy interfejs i 5 implementacji. 
Wybór tej właściwej na podstawie jakiejś flagi (np. "dostawca płatności") w Springu robi się przez @Qualifier lub mapy Beanów.

Cel: 
Stworzenie systemu "Pluginów Biznesowych", gdzie serwis automatycznie wybiera strategię przetwarzania danych na podstawie typu obiektu.

Zadanie: 
Stwórz `OrderProcessor`, który na podstawie typu Order wybiera strategię: 
 - StandardOrderStrategy
 - VIPOrderStrategy
 - GlobalOrderStrategy
 - 
Wyzwanie Mid-a: 
Zrób to w sposób "Open/Closed" – czyli dodanie nowej strategii ma wymagać stworzenia tylko jednej klasy, 
            bez modyfikacji głównego serwisu.

Zamiast pisać wielkiego `if` albo `switch`, zrobimy tak, aby każda nowa strategia sama "zgłaszała się" do serwisu.
1. Interfejs Strategii
   Stwórz `src/main/groovy/pl/edu/praktyki/strategy/OrderStrategy.groovy`:


2. Implementacje (np. dwie z trzech)
   Stwórz `src/main/groovy/pl/edu/praktyki/strategy/StandardOrderStrategy.groovy`:

```groovy
package pl.edu.praktyki.strategy
import org.springframework.stereotype.Component

@Component
class StandardOrderStrategy implements OrderStrategy {
    boolean supports(String type) { type == "STANDARD" }
    void process(BigDecimal amount) { println "Standardowe przetwarzanie kwoty: $amount" }
}
```
(Analogicznie stwórz VIPOrderStrategy z supports zwracającym "VIP").

3. Główny Serwis `OrderProcessor.groovy`
   Używamy tutaj "Spring Magic" – Spring automatycznie zbierze wszystkie Beany implementujące OrderStrategy do listy!

```groovy
package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pl.edu.praktyki.strategy.OrderStrategy

@Service
class OrderProcessor {

    // Spring wstrzyknie WSZYSTKIE implementacje!
    // <--- wrzuca tu WSZYSTKIE Beany typu OrderStrategy!
    @Autowired List<OrderStrategy> strategies

    void process(String type, BigDecimal amount) {

        // Groovy: Znajdź strategię, która mówi 'true' na metodę supports
        def strategy = strategies.find { it.supports(type) }

        // Zabezpieczamy się przed nullem:
        if (strategy == null) {
            throw new IllegalArgumentException("Nieznany typ zamówienia: $type")
        }

        // tutaj już mamy konkretną strategię, więc możemy ją wywołać
        // magia Groovy: nie musimy rzutować, bo wiemy, że to OrderStrategy juz konkretnego typu:
        // np StandardOrderStrategy lub VipOrderStrategy
        strategy.process(amount)
    }
}
```

Dlaczego to jest Open/Closed? 
Gdy dodasz `GlobalOrderStrategy`, nie zmieniasz `OrderProcessor`. 
Spring sam go wykryje i doda do listy strategies.

Test-1:
-------

Stwórz `src/test/groovy/pl/edu/praktyki/strategy/OrderProcessorSpec.groovy`:

```groovy
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
```


Test-2:
-------

Stwórz `src/test/groovy/pl/edu/praktyki/service/OrderProcessorSpec.groovy`:

```groovy
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
        ex.message == "Brak strategii dla: UNKNOWN"
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
```