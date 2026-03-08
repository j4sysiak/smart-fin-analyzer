package pl.edu.praktyki.monitoring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

class FinanceMetricsSpec extends Specification {

    def "powinien poprawnie aktualizować wskaźnik bilansu (Gauge)"() {
        given: "czysty rejestr metryk"
        def registry = new SimpleMeterRegistry()
        def metrics = new FinanceMetrics(registry)

        when: "aktualizujemy bilans"
        metrics.updateBalance(1234.56)

        then: "wskaźnik w rejestrze zwraca wartość 1234.56"
        // SimpleMeterRegistry pozwala nam wyciągnąć gauge i sprawdzić jego wartość
        registry.get("smartfin.transactions.total.balance").gauge().value() == 1234.56
    }

    def "powinien inkrementować licznik transakcji"() {
        given:
        def registry = new SimpleMeterRegistry()
        def metrics = new FinanceMetrics(registry)

        when:
        metrics.recordTransaction(100.0)
        metrics.recordTransaction(50.0)

        then:
        registry.get("smartfin.transactions.processed")
                .counter()
                .count() == 2.0
    }
}