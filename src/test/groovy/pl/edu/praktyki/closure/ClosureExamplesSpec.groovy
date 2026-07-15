package pl.edu.praktyki.closure

import spock.lang.Specification

class ClosureExamplesSpec extends Specification {

    def "powinien policzyć kwadrat liczby"() {
        given:
        def square = { x ->
            x * x
        }

        expect:
        square(4) == 16
    }

    def "powinien podwoić elementy listy"() {
        given:
        def numbers = [1, 2, 3, 4]

        expect:
        numbers.collect { it * 2 } == [2, 4, 6, 8]
    }

    def "powinien sprawdzić pełnoletność"() {
        given:
        def isAdult = { age ->
            age >= 18
        }

        expect:
        isAdult(20)
        !isAdult(15)
    }

    def "powinien użyć zmiennej z zewnątrz"() {
        given:
        def multiplier = 3
        def multiply = { x ->
            x * multiplier
        }

        expect:
        multiply(5) == 15
    }

    def "powinien uruchomić akcję dwa razy"() {
        given:
        def calls = 0
        def repeatTwice = { action ->
            action()
            action()
        }

        when:
        repeatTwice {
            calls++
        }

        then:
        calls == 2
    }
}