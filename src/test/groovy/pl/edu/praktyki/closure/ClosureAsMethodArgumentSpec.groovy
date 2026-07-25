package pl.edu.praktyki.closure

import spock.lang.Specification

class ClosureAsMethodArgumentSpec extends Specification {

    def runner = new ActionRunner()
    /*
    Idea testu:
    - `execute` przyjmuje closure jako argument
    - w środku po prostu je uruchamia
    - dzięki temu kod przekazany w bloku `execute { ... }` wykona się "później"
    */

    def "powinien uruchomić closure przekazaną do metody"() {
        given:
        // to jest pierwszy closure przypisany do zmiennej execute
        // i wykonuje to, co przekazaliśmy w bloku { calls++ }
        // i wtedy licznik wzrasta o 1
        def execute = { Closure action -> action() }
        def calls = 0

        when:
        // a to jest drugi closure, który jest przekazany do execute, to jest właśnie ten blok { calls++ }
        // to znaczy, że przekazujemy do execute "coś do zrobienia później"
        // i execute wywoła to, co przekazaliśmy, czyli wykona ten blok { calls++ }
        // i wtedy licznik wzrośnie o 1
        runner.execute { calls++ }
        // tak można zapisać bardziej dydaktycznie:
        //runner.execute({ calls++ })
        // inny przykład użycia execute, który po prostu wypisuje "hej" na konsolę:
        //runner.execute { println "hej" }

        // można też użyć closure przypisanego do zmiennej \execute` w bloku `given`, czyli:
        // execute { calls++ }


        then:
        // skoro execute wywołało action(), licznik wzrasta o 1
        calls == 1
    }

    def "powinien przekazać dane i closure do metody"() {
        given:
        def greet = {  name, a -> a(name)  }

        def result = null

        when:
        runner.greet("Ania") { person -> result = "Cześć, $person"}
        // tak można zapisać bardziej dydaktycznie:
        // runner.greet("Ania", { person -> result = "Cześć, $person" })

        then:
        result == "Cześć, Ania"
    }
}