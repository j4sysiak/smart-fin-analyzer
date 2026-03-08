Nazwa "Wzorce projektowe" (Design Patterns) brzmi mądrze, 
 ale to po prostu "gotowe przepisy na rozwiązanie powtarzalnych problemów".

Wyobraź sobie, że budujesz system obsługi płatności.

1. Klient płaci kartą -> inny kod.
2. Klient płaci BLIK-iem -> inny kod.
3. Klient płaci przelewem -> jeszcze inny kod.

1. Co to jest Wzorzec Strategii (Strategy Pattern)?

To po prostu sposób, żeby uniknąć gigantycznego if-else:

```groovy
// TAK NIE ROBIMY (To jest słaby kod):
if (type == "BLIK") { ... }
else if (type == "KARTA") { ... }
else if (type == "PRZELEW") { ... }
```


Zamiast tego tworzysz "Strategię" dla każdej metody płatności. 
Każda strategia to osobna klasa, która wie tylko jak obsłużyć swoją płatność.

2. Jak to działa w Springu (Auto-detection)?

To jest "magia", którą wykorzystują Seniorzy. 
Jeśli stworzysz 3 klasy (np. BlikStrategy, KartaStrategy, PrzelewStrategy) 
i wszystkie będą miały adnotację @Component, to Spring automatycznie wrzuci je wszystkie do jednej listy: List<OrderStrategy>.

W `OrderProcessor` po prostu przeszukujesz tę listę:
"Kto z was obsługuje BLIKa?". 
Dzięki temu, gdy firma wprowadzi nową metodę płatności (np. Apple Pay), 
tylko dodajesz nową klasę, a głównego serwisu (OrderProcessor) nawet nie dotykasz.

3. Zaimplementujmy to (Krok po kroku)
   Klasa Strategii (Interfejs):

`src/main/groovy/pl/edu/praktyki/strategy/OrderStrategy.groovy`

```groovy
package pl.edu.praktyki.strategy

interface OrderStrategy {
boolean supports(String type) // Czy ta klasa obsługuje ten typ?
void process(BigDecimal amount) // Logika dla tego typu
}
```

Przykład Strategii:

`src/main/groovy/pl/edu/praktyki/strategy/VipOrderStrategy.groovy`

```groovy
package pl.edu.praktyki.strategy
import org.springframework.stereotype.Component

@Component
class VipOrderStrategy implements OrderStrategy {
boolean supports(String type) { type == "VIP" }
void process(BigDecimal amount) { println "VIP otrzymał zniżkę 20%: ${amount * 0.8}" }
}
```

Serwis, który "magicznie" zbiera strategie:

`src/main/groovy/pl/edu/praktyki/strategy/OrderProcessor.groovy`

```groovy
package pl.edu.praktyki.strategy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class OrderProcessor {
@Autowired List<OrderStrategy> strategies // <--- Spring wrzuca tu WSZYSTKIE Beany typu OrderStrategy!

    void process(String type, BigDecimal amount) {
        // Groovy: Znajdź strategię, która mówi 'true' na metodę supports
        def strategy = strategies.find { it.supports(type) }
        
        if (!strategy) throw new IllegalArgumentException("Nieznany typ zamówienia: $type")
        
        strategy.process(amount)
    }
}
```

4. Test Spock (To Cię oświeci!)

Stwórz `src/test/groovy/pl/edu/praktyki/strategy/OrderProcessorSpec.groovy`:

```groovy
package pl.edu.praktyki.strategy

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest(classes = [OrderProcessor, VipOrderStrategy]) // Spring uruchamia kontekst
class OrderProcessorSpec extends Specification {

    @Autowired OrderProcessor orderProcessor

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

Podsumowanie dla Ciebie:

Wzorzec Strategii: Rozbicie dużej logiki na małe, dedykowane klasy.

Auto-detection: 
Spring automatycznie zbiera te klasy w List<OrderStrategy>.

Open/Closed: 
Chcesz dodać nową strategię? 
Tworzysz nowy plik .groovy z @Component.
Starego kodu nie zmieniasz. 
To jest święty Graal czystego kodu.

