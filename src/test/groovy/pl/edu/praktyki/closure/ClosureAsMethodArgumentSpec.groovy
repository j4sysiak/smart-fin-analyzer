package pl.edu.praktyki.closure

import spock.lang.Specification

class ClosureAsMethodArgumentSpec extends Specification {

    /*
    a() na linii 31:
    zakłada, że a jest closure
    uruchamia ją bez argumentów
    powoduje wykonanie kodu przekazanego później w bloku execute { ... }
    Przepływ jest taki:
    execute przyjmuje jeden argument a
    przy wywołaniu execute { calls++ } ten blok staje się właśnie a
    linia a() wykonuje ten blok
    więc calls zwiększa się o 1
     */


    def "powinien uruchomić closure przekazaną do metody"() {
        given:
        // execute przyjmuje jeden argument action
        // Linia 30 definiuje closure i przypisuje ją do zmiennej execute.
        // { a -> ... } — ciało closure
        // a — parametr closure, który będzie przekazany przy wywołaniu execute
        // -> — oddziela parametry od wykonywanego kodu
        // W praktyce oznacza to:
        // - execute przechowuje funkcję anonimową
        // - ta funkcja przyjmuje jeden argument a
        // - wewnątrz wykonuje a(), więc zakłada, że a też jest wywoływalne, np. jest closure
        def execute = { a ->
            a()
        }
        def calls = 0

        when:
        execute {
            calls++
        }

        then:
        calls == 1
    }

    def "powinien przekazać dane i closure do metody"() {
        given:
        def greet = { name, a ->
            a(name)
        }
        def result = null

        when:
        greet("Ania") { person ->
            result = "Cześć, $person"
        }

        then:
        result == "Cześć, Ania"
    }
}