package pl.edu.praktyki.closure

import spock.lang.Specification

class ClosureExamplesSpec extends Specification {

    // klasa produkcyjna — nie definiujemy logiki w teście
    def examples = new ClosureExamples()

    def "powinien policzyć kwadrat liczby"() {
        expect:
        examples.square(4) == 16
    }

    def "powinien podwoić elementy listy"() {
        given:
        def numbers = [1, 2, 3, 4]

        expect:
        examples.doubleElements(numbers) == [2, 4, 6, 8]
    }

    def "powinien sprawdzić pełnoletność"() {
        expect:
        examples.isAdult(20)
        !examples.isAdult(15)
    }

    def "powinien użyć zmiennej z zewnątrz (closure capture)"() {
        given:
        // multiplier pochodzi "z zewnątrz" — przekazujemy go do metody
        def multiplier = 3

        expect:
        examples.multiply(5, multiplier) == 15
    }

    def "powinien uruchomić akcję dwa razy"() {
        given:
        def calls = 0

        when:
        // przekazujemy closure do metody produkcyjnej
        examples.repeatTwice { calls++ }

        then:
        calls == 2
    }
}