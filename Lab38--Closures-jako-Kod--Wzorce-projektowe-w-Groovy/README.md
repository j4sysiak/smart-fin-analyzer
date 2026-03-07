Lab 38
------

przechodzimy do Poziomu Hardcore. 
Zostawiamy Springa w spokoju i uderzamy w niskopoziomową magię JVM, którą Groovy pozwala ujarzmić w elegancki sposób.

Oto plan na Fazę 5: Java Interop & Meta-Programming Advanced.

Lab 38: Closures jako "Kod" (Wzorce projektowe w Groovy)
--------------------------------------------------------

W Javie wzorzec `Strategy` to kilka klas i interfejsów. 
W Groovy to jeden `Closure`. 
Nauczysz się, jak budować elastyczne systemy wtyczkowe.

Zadanie: 
System wtyczek ("Plugin System")

Cel: 
Stworzenie systemu, który pozwala na "wstrzykiwanie" logiki do Twojego serwisu w trakcie działania programu. 
Nasz PluginManager nie będzie wiedział nic o tym, co konkretnie ma zrobić z danymi – zrobi to, co mu "podrzucisz" w formie domknięcia.

Krok 1: Implementacja PluginManager
-----------------------------------
Stwórz klasę `PluginManager`, co mają robić wtyczki, dopóki użytkownik ich nie "wstrzyknie".

Stwórz plik `src/main/groovy/pl/edu/praktyki/plugin/PluginManager.groovy`.
 

```groovy
package pl.edu.praktyki.plugin

/**
 * Manager, który przechowuje i wykonuje listę wtyczek.
 * Każda wtyczka to domknięcie (Closure), które przyjmuje dane (np. Transaction).
 */
class PluginManager {
    private List<Closure> plugins = []

    // Dodawanie nowej logiki w czasie działania programu
    void addPlugin(Closure plugin) {
        plugins << plugin
    }

    // Wykonanie wszystkich wtyczek na raz
    void runAll(Object data) {
        plugins.each { plugin ->
            try {
                plugin(data)
            } catch (Exception e) {
                println "[PLUGIN ERROR] Wtyczka zawiodła: ${e.message}"
            }
        }
    }
}
```

Krok 2: Test Spock (PluginSpec.groovy)
--------------------------------------

To tutaj zobaczymy, jak potężne jest to rozwiązanie. 
Zamiast tworzyć klasy EmailLogger, SlackNotifier, AuditManager itd., po prostu definiujemy ich logikę w teście.
Stwórz `src/test/groovy/pl/edu/praktyki/plugin/PluginSpec.groovy`:

 

```groovy
package pl.edu.praktyki.plugin

import spock.lang.Specification
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

class PluginSpec extends Specification {

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
    
    def "powinien uruchomić wtyczki z logiką użytkownika"() {
        given: "manager"
        def pm = new PluginManager()

        and: "wtyczki zdefiniowane w locie"
        pm.addPlugin { println "Logowanie: $it" }
        pm.addPlugin { if (it.size() > 5) println "Alert: Długi tekst!" }

        when:
        pm.runAll("Ala ma kota")

        then:
        noExceptionThrown()
    }
}
```

Wyzwanie Finałowe Lab 27:
-------------------------
Twoim zadaniem jest stworzenie "Wtyczki Filtrującej".
Zmień metodę runAll(Object data) w `PluginManager` tak, aby wtyczki mogły zmieniać dane (np. dodawać tagi do transakcji).

Zaimplementuj wtyczkę, która dla każdej transakcji z kategorią IT dodaje tag TECH.

Napisz test Spocka, który przekaże transakcję do pm.runAll(tx) i sprawdzi, czy po wyjściu z tej metody transakcja faktycznie ma tag TECH.

Podpowiedź: 
W Groovy, jeśli przekazujesz obiekt do metody, operujesz na jego referencji (czyli bezpośrednio na tym obiekcie). 
Wewnątrz Closure po prostu wywołaj `data.addTag('TECH')`.
Daj znać, jak poszło z wtyczką filtrującą!
To rozwiązanie jest fundamentem "Plugin Architecture" – bardzo cenionego wzorca w dużych systemach enterprise. 
Czy udało Ci się dodać tag przez PluginManager?


Rozwiazanie Finałowe Lab 27:
-------------------------

Wyzwanie to pokazuje, jak łatwo w Groovym można modyfikować obiekty „w locie” za pomocą przekazywanych wtyczek. 
Nie potrzebujemy do tego żadnych skomplikowanych wzorców (jak Strategy czy Decorator), wystarczy nam Closure i referencja do obiektu.

1. Implementacja `PluginManager.groovy`
   Metoda runAll w PluginManager pozostaje taka sama, ponieważ w Groovy obiekty są przekazywane przez referencję 
                      – modyfikując obiekt w Closure, modyfikujemy go „na zewnątrz”.







