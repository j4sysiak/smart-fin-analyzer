package pl.edu.praktyki.strategy.second

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

/*
  W teście Spock używasz @ContextConfiguration zamiast pełnego @SpringBootTest,
  więc Spring nie skanuje automatycznie całego pakietu w poszukiwaniu beanów.
  Musisz mu jawnie powiedzieć, które klasy mają tworzyć kontekst aplikacji.
  Dlatego podajesz te 3 klasy:

Klasa                 | Dlaczego jest potrzebna
----------------------+----------------------------------------------------------------------------------------------------------------------
TransactionProcessor  | To jest testowany serwis (@Service), który chcesz wstrzyknąć przez @Autowired
TransferStrategy      | To jest @Component implementujący TransactionStrategy — bez niego lista strategies byłaby pusta
PaymentStrategy       | Drugi @Component implementujący TransactionStrategy — bez niego test processor.strategies.size() == 2 by nie przeszedł

Mechanizm działania:
Spring tworzy mini-kontekst zawierający tylko te 3 beany.
        TransactionProcessor ma pole @Autowired List<TransactionStrategy> strategies.
Spring znajduje w tym mini-kontekście dwa beany implementujące TransactionStrategy (TransferStrategy i PaymentStrategy) i wstrzykuje je do listy.
*/

@ContextConfiguration(classes = [TransactionProcessor, TransferStrategy, PaymentStrategy])
class TransactionProcessorSpec extends Specification {

    @Autowired
    TransactionProcessor processor

    def "powinien wstrzyknąć wszystkie strategie"() {
        expect:
        processor.strategies.size() == 2
    }

    def "powinien automatycznie użyć odpowiedniej strategii dla TRANSFER"() {
        when: "wywołujemy przelew"
        processor.process("TRANSFER", 100)

        then: "nie ma błędu"
        noExceptionThrown()
    }
}
