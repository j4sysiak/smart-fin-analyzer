package pl.edu.praktyki.plugin

import spock.lang.Specification
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

class PluginSpec extends Specification {

    def "powinien uruchomić wtyczki z logiką użytkownika"() {
        given: "manager"
        def pm = new PluginManager()

        and: "wtyczki zdefiniowane w locie"
        pm.addPlugin { println "Logowanie: $it" }
        pm.addPlugin { if(it.size() > 5) println "Alert: Długi tekst!" }

        when:
        pm.runAll("Ala ma kota")

        then:
        noExceptionThrown()

        and:
        then: "Wtyczki działają bez błędów"
            // W tym teście nie sprawdzamy bezpośrednio wyjścia, ale upewniamy się,
            // że wtyczki działają bez błędów
            // Można by też przechwycić wyjście konsoli i sprawdzić jego zawartość,
            // ale to jest bardziej zaawansowane
            true
    }

    def "should add plugins and execute them with provided data"() {
        given: "A PluginManager instance"
        def pluginManager = new PluginManager()

        and: "Two plugins that modify a shared list"
        def result = []
        pluginManager.addPlugin { data -> result << "Plugin1: $data" }
        pluginManager.addPlugin { data -> result << "Plugin2: $data" }

        when: "All plugins are executed with some data"
        pluginManager.runAll("testData")

        then: "The result list contains outputs from both plugins"
        result == ["Plugin1: testData", "Plugin2: testData"]
    }

    def "powinien uruchomić zestaw dynamicznych wtyczek dla transakcji"() {
        given: "nasz PluginManager"
        def pm = new PluginManager()

        and: "dane transakcji"
        def tx = new Transaction(id: "TX-99", amount: 200.0, category: "IT", description: "Monitor")

        and: "dodajemy wtyczki (logika przekazana jako Closure)"
        pm.addPlugin { Transaction t ->
            println "PLUGIN 1: Wysyłam maila o transakcji: ${t.id}"
        }

        pm.addPlugin { Transaction t ->
            if (t.amount > 100) println "PLUGIN 2: Alarm! Wysoki wydatek: ${t.amount}"
        }

        when: "uruchamiamy wszystkie wtyczki"
        pm.runAll(tx)

        then: "nie powinno być żadnych błędów"
        noExceptionThrown()
    }

    def "Wyzwanie: Wtyczka filtrująca powinna modyfikować transakcję (dodać tag TECH)"() {
        given: "Manager wtyczek i transakcja z kategorii IT"
        def pm = new PluginManager()
        def tx = new Transaction(id: "TX-100", amount: 500.0, category: "IT", description: "Monitor")

        and: "Wtyczka, która sprawdza kategorię i dodaje tag"
        pm.addPlugin { Transaction t ->
            if (t.category == "IT") {
                t.addTag("TECH")
            }
        }

        when: "uruchamiamy wszystkie wtyczki"
        pm.runAll(tx)

        then: "transakcja posiada tag TECH"
        tx.tags.contains("TECH")

        and: "transakcja z innej kategorii NIE powinna mieć tego tagu"
        def otherTx = new Transaction(id: "TX-200", amount: 50.0, category: "Dom")
        pm.runAll(otherTx)
        !otherTx.tags.contains("TECH")
    }
}