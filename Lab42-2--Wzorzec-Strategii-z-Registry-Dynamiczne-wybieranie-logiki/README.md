Lab 42-2
------

Lab42-2--Wzorzec-Strategii-z-Registry-Dynamiczne-wybieranie-logiki
------------------------------------------------------------------


Wzorzec, który w świecie Springa i przetwarzania danych jest absolutnym fundamentem: Wzorzec Strategy (z wariantem Registry).

Dla programisty Java/Groovy (szczególnie w Springu) jest to "must-have", 
ponieważ pozwala tworzyć systemy, które są nieskończenie rozszerzalne bez edytowania głównego kodu.


Cel: 
Stworzymy system obsługi różnych typów transakcji (np. Transfer, Payment, Withdrawal). 
Zamiast if-else czy switch, każda strategia sama powie Springowi: "Ja obsługuję ten typ transakcji".

1. Interfejs Strategii (TransactionStrategy.groovy)

`src/main/groovy/pl/edu/praktyki/strategy/second/TransactionStrategy.groovy`

```groovy
package pl.edu.praktyki.strategy.second

interface TransactionStrategy {
    boolean supports(String type)
    void execute(BigDecimal amount)
}
```

2. Konkretne Strategie

Stwórz dwie klasy w `src/main/groovy/pl/edu/praktyki/strategy/second`.

TransferStrategy.groovy:

```groovy
package pl.edu.praktyki.strategy.second

import org.springframework.stereotype.Component

@Component
class TransferStrategy implements TransactionStrategy {

    boolean supports(String type) {
        type == "TRANSFER"
    }

    void execute(BigDecimal amount) {
        println "Przelew: $amount PLN"
    }
}
```
 

PaymentStrategy.groovy:

```groovy
package pl.edu.praktyki.strategy.second

import org.springframework.stereotype.Component

@Component
class PaymentStrategy implements TransactionStrategy {

    boolean supports(String type) {
        type == "PAYMENT"
    }

    void execute(BigDecimal amount) {
        println "Płatność kartą: $amount PLN"
    }
}
```


3. "Mózg" - Strategiczny Procesor (TransactionProcessor.groovy)

To jest najważniejszy moment. 
Zwróć uwagę, że Spring wstrzyknie nam listę wszystkich implementacji interfejsu.

```groovy
package pl.edu.praktyki.strategy.second

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class TransactionProcessor {
    // Spring automatycznie zbierze tu wszystkie Beany, które implementują TransactionStrategy
    @Autowired List<TransactionStrategy> strategies

    void process(String type, BigDecimal amount) {
        def strategy = strategies.find { it.supports(type) }
        if (!strategy) throw new IllegalArgumentException("Brak strategii dla: $type")

        strategy.execute(amount)
    }
}

```

Test Spock (Integracyjny z automatycznym wykrywaniem)
-----------------------------------------------------

Zamiast ręcznie wstrzykiwać listę, pozwólmy Springowi podnieść kontekst i znaleźć wszystkie strategie.

`src/test/groovy/pl/edu/praktyki/strategy/second/TransactionProcessorSpec.groovy`

```groovy
package pl.edu.praktyki.strategy.second

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ContextConfiguration(classes = [TransactionProcessor, TransferStrategy, PaymentStrategy])
class TransactionProcessorSpec extends Specification {

    @Autowired TransactionProcessor processor

    def "powinien automatycznie użyć odpowiedniej strategii"() {
        expect:
        // Sprawdzamy, czy w ogóle Spring wstrzyknął strategie
        processor.strategies.size() == 2

        when: "wywołujemy przelew"
        processor.process("TRANSFER", 100)

        then: "nie ma błędu"
        noExceptionThrown()
    }
}

```



Wyzwanie Finałowe Lab 40/41:

Zadanie: Dodaj nową klasę RefundStrategy, która obsługuje typ "REFUND".

Co jest w tym "Senioralnego"? Zauważ, że nie dotykasz kodu TransactionProcessor. Kod procesora jest "zamknięty na modyfikacje" (Zasada Open/Closed). Wystarczy, że dodasz nowy plik z @Component, a system sam zacznie go używać.

Test: Napisz test, który spróbuje użyć typu "REFUND" i potwierdzi, że zadziałało.

Dlaczego to "must-have"?

W systemach klasy Enterprise (np. systemy płatności) będziesz miał dziesiątki takich strategii. Dzięki takiemu podejściu Twój TransactionProcessor pozostaje krótki i czytelny (ok. 10 linii kodu), niezależnie od tego, czy masz 2, czy 200 strategii płatności.

Czy wdrożenie tego "mechanizmu wtyczkowego" jest dla Ciebie jasne? Jeśli tak, to w zasadzie masz kompletny zestaw narzędzi architektonicznych! 🏗️✅

